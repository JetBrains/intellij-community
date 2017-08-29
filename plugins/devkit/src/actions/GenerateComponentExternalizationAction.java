/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.actions;

import com.intellij.codeInsight.actions.CodeInsightEditorAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

public class GenerateComponentExternalizationAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(GenerateComponentExternalizationAction.class);

  @NonNls private final static String BASE_COMPONENT = "com.intellij.openapi.components.BaseComponent";
  @NonNls private final static String PERSISTENCE_STATE_COMPONENT = "com.intellij.openapi.components.PersistentStateComponent";
  @NonNls private final static String STATE = "com.intellij.openapi.components.State";
  @NonNls private final static String STORAGE = "com.intellij.openapi.components.Storage";

  @Override
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    CodeInsightEditorAction.beforeActionPerformedUpdate(e);
    super.beforeActionPerformedUpdate(e);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final PsiClass target = getComponentInContext(e.getDataContext());
    assert target != null;

    final PsiElementFactory factory = JavaPsiFacade.getInstance(target.getProject()).getElementFactory();
    final CodeStyleManager formatter = CodeStyleManager.getInstance(target.getManager().getProject());
    final JavaCodeStyleManager styler = JavaCodeStyleManager.getInstance(target.getProject());
    final String qualifiedName = target.getQualifiedName();
    Runnable runnable = () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        final PsiReferenceList implList = target.getImplementsList();
        assert implList != null;
        final PsiJavaCodeReferenceElement referenceElement =
          factory.createReferenceFromText(PERSISTENCE_STATE_COMPONENT + "<" + qualifiedName + ">", target);
        implList.add(styler.shortenClassReferences(referenceElement.copy()));
        PsiMethod read = factory.createMethodFromText(
          "public void loadState(" + qualifiedName + " state) {\n" +
          "    com.intellij.util.xmlb.XmlSerializerUtil.copyBean(state, this);\n" +
          "}",
          target
        );

        read = (PsiMethod)formatter.reformat(target.add(read));
        styler.shortenClassReferences(read);

        PsiMethod write = factory.createMethodFromText(
          "public " + qualifiedName + " getState() {\n" +
          "    return this;\n" +
          "}\n",
          target
        );
        write = (PsiMethod)formatter.reformat(target.add(write));
        styler.shortenClassReferences(write);

        PsiAnnotation annotation = target.getModifierList().addAnnotation(STATE);

        annotation = (PsiAnnotation)formatter.reformat(annotation.replace(
          factory.createAnnotationFromText("@" + STATE +
                                           "(name = \"" + qualifiedName + "\", " +
                                           "storages = {@" + STORAGE + "(file = \"" + StoragePathMacros.WORKSPACE_FILE + "\"\n )})",
                                           target)));
        styler.shortenClassReferences(annotation);
      }
      catch (IncorrectOperationException e1) {
        LOG.error(e1);
      }
    });

    CommandProcessor.getInstance().executeCommand(target.getProject(), runnable,
                                                  DevKitBundle.message("command.implement.externalizable"), null);
  }

  @Nullable
  private static PsiClass getComponentInContext(DataContext context) {
    Editor editor = CommonDataKeys.EDITOR.getData(context);
    Project project = CommonDataKeys.PROJECT.getData(context);
    if (editor == null || project == null) return null;

    PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
    if (file == null) return null;

    PsiClass contextClass = PsiTreeUtil.findElementOfClassAtOffset(file, editor.getCaretModel().getOffset(), PsiClass.class, false);
    if (contextClass == null || contextClass.isEnum() || contextClass.isInterface() || contextClass instanceof PsiAnonymousClass) {
      return null;
    }

    final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(file.getProject());
    PsiClass componentClass = javaPsiFacade.findClass(BASE_COMPONENT, file.getResolveScope());
    if (componentClass == null || !contextClass.isInheritor(componentClass, true)) return null;

    PsiClass persistenceStateComponentClass = javaPsiFacade.findClass(PERSISTENCE_STATE_COMPONENT, file.getResolveScope());
    if (persistenceStateComponentClass == null || contextClass.isInheritor(persistenceStateComponentClass, true)) return null;

    return contextClass;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final PsiClass target = getComponentInContext(e.getDataContext());

    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(target != null);
  }
}

