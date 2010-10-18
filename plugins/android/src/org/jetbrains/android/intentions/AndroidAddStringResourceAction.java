/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android.intentions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import static com.intellij.openapi.ui.Messages.showInputDialog;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import static org.jetbrains.android.util.AndroidUtils.CONTEXT;
import static org.jetbrains.android.util.AndroidUtils.VIEW_CLASS_NAME;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Mar 9, 2009
 * Time: 5:02:31 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidAddStringResourceAction extends AbstractIntentionAction {
  @NotNull
  public String getText() {
    return AndroidBundle.message("add.string.resource.intention.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return AndroidBundle.message("intention.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    AndroidFacet facet = AndroidFacet.getInstance(file);
    PsiElement element = getPsiElement(file, editor);
    if (facet == null || element == null) return false;
    PsiClass c = getContainingInheritorOf(element, VIEW_CLASS_NAME, CONTEXT);
    if (c != null && getStringLiteralValue(element, file) != null) {
      return !RefactoringUtil.isInStaticContext(element, c);
    }
    return false;
  }

  @Nullable
  private static String getStringLiteralValue(@NotNull PsiElement element, @NotNull PsiFile file) {
    if (file instanceof PsiJavaFile) {
      PsiClass c = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (c == null || !HighlightClassUtil.hasEnclosingInstanceInScope(c, element, false)) {
        return null;
      }
      if (element instanceof PsiLiteralExpression) {
        PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
        Object value = literalExpression.getValue();
        if (value instanceof String) {
          return (String)value;
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiClass getContainingInheritorOf(@NotNull PsiElement element, @NotNull String... baseClassNames) {
    PsiClass c = null;
    do {
      c = PsiTreeUtil.getParentOfType(c == null ? element : c, PsiClass.class);
      for (String name : baseClassNames) {
        if (InheritanceUtil.isInheritor(c, name)) {
          return c;
        }
      }
    }
    while (c != null);
    return null;
  }

  @Nullable
  private static PsiElement getPsiElement(PsiFile file, Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    return element != null ? element.getParent() : null;
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    AndroidFacet facet = AndroidFacet.getInstance(file);
    assert facet != null;
    PsiElement element = getPsiElement(file, editor);
    assert element != null;
    String value = getStringLiteralValue(element, file);
    assert value != null;
    String aPackage = getPackage(facet);
    if (aPackage == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("package.not.found.error"), CommonBundle.getErrorTitle());
      return;
    }
    String resName =
      showInputDialog(project, AndroidBundle.message("resource.name"), AndroidBundle.message("add.string.resource.intention.text"),
                      Messages.getQuestionIcon(), "", new InputValidatorEx() {
          public String getErrorText(String inputString) {
            if (inputString == null || inputString.length() == 0) return null;
            String[] ids = inputString.split(".");
            for (String id : ids) {
              if (!StringUtil.isJavaIdentifier(id)) {
                return AndroidBundle.message("android.identifier.expected", id);
              }
            }
            return null;
          }

          public boolean checkInput(String inputString) {
            return inputString != null && AndroidResourceUtil.isCorrectAndroidResourceName(inputString);
          }

          public boolean canClose(String inputString) {
            return checkInput(inputString);
          }
        });
    if (resName == null) return;
    LocalResourceManager manager = facet.getLocalResourceManager();
    String resType = "string";
    assert manager.addValueResource(resType, resName, value) != null;
    boolean extendsContext = getContainingInheritorOf(element, CONTEXT) != null;
    PsiExpression newExpression = createJavaResourceReference(project, aPackage, resType, resName, extendsContext);
    element.replace(newExpression);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(newExpression);
    UndoUtil.markPsiFileForUndo(file);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().saveAll();
      }
    });
  }

  @Nullable
  private static String getPackage(@NotNull AndroidFacet facet) {
    Manifest manifest = facet.getManifest();
    if (manifest == null) return null;
    return manifest.getPackage().getValue();
  }

  @Nullable
  private static String getGetterNameForResourceType(@NotNull String type) {
    if (type.length() < 2) return null;
    if (type.equals("dimen")) {
      return "getDimension";
    }
    return "get" + Character.toUpperCase(type.charAt(0)) + type.substring(1);
  }

  @NotNull
  private static PsiExpression createJavaResourceReference(@NotNull Project project,
                                                           @NotNull String aPackage,
                                                           @NotNull String resType,
                                                           @NotNull String resName,
                                                           boolean extendsContext) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiElementFactory factory = facade.getElementFactory();
    String field = aPackage + ".R." + resType + '.' + AndroidResourceUtil.getRJavaFieldName(resName);
    String methodName = getGetterNameForResourceType(resType);
    assert methodName != null;
    String s = "getResources()." + methodName + "(" + field + ")";
    if (!extendsContext) s = "getContext()." + s;
    return factory.createExpressionFromText(s, null);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
