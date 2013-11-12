/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.declaration;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.codeInsight.intention.impl.CreateSubclassAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.annotator.intentions.CreateClassActionBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;

/**
 * @author Max Medvedev
 */
public class GrCreateSubclassAction extends CreateSubclassAction {
  private static final Logger LOG = Logger.getInstance(GrCreateSubclassAction.class);

  @Override
  protected boolean isSupportedLanguage(PsiClass aClass) {
    return aClass.getLanguage() == GroovyFileType.GROOVY_LANGUAGE;
  }

  @Override
  protected void createTopLevelClass(PsiClass psiClass) {
    final CreateClassDialog dlg = chooseSubclassToCreate(psiClass);
    if (dlg != null) {
      createSubclassGroovy((GrTypeDefinition)psiClass, dlg.getTargetDirectory(), dlg.getClassName());
    }
  }

  @Nullable
  public static PsiClass createSubclassGroovy(final GrTypeDefinition psiClass, final PsiDirectory targetDirectory, final String className) {
    final Project project = psiClass.getProject();
    final Ref<GrTypeDefinition> targetClass = new Ref<GrTypeDefinition>();

    new WriteCommandAction(project, getTitle(psiClass), getTitle(psiClass)) {
      @Override
      protected void run(Result result) throws Throwable {
        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

        final GrTypeParameterList oldTypeParameterList = psiClass.getTypeParameterList();

        try {
          targetClass.set(CreateClassActionBase.createClassByType(targetDirectory, className, PsiManager.getInstance(project), psiClass,
                                                                  GroovyTemplates.GROOVY_CLASS, true));
        }
        catch (final IncorrectOperationException e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog(project, CodeInsightBundle.message("intention.error.cannot.create.class.message", className) +
                                                "\n" + e.getLocalizedMessage(),
                                       CodeInsightBundle.message("intention.error.cannot.create.class.title"));
            }
          });
          return;
        }
        startTemplate(oldTypeParameterList, project, psiClass, targetClass.get(), false);
        PsiElement element = targetClass.get();
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(element);
      }
    }.execute();
    if (targetClass.get() == null) return null;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !psiClass.hasTypeParameters()) {

      final Editor editor = CodeInsightUtil.positionCursor(project, targetClass.get().getContainingFile(), targetClass.get().getLBrace());
      if (editor == null) return targetClass.get();
      chooseAndImplement(psiClass, project, targetClass.get(), editor);
    }
    return targetClass.get();
  }


  private static void startTemplate(GrTypeParameterList oldTypeParameterList,
                                    final Project project,
                                    final GrTypeDefinition psiClass,
                                    final GrTypeDefinition targetClass,
                                    boolean includeClassName) {
    PsiElementFactory jfactory = JavaPsiFacade.getElementFactory(project);
    final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(project);
    GrCodeReferenceElement ref = elementFactory.createCodeReferenceElementFromClass(psiClass);
    try {
      if (psiClass.isInterface()) {
        GrImplementsClause clause = targetClass.getImplementsClause();
        if (clause == null) {
          clause = (GrImplementsClause)targetClass.addAfter(elementFactory.createImplementsClause(), targetClass.getNameIdentifierGroovy());
        }
        ref = (GrCodeReferenceElement)clause.add(ref);
      }
      else {
        GrExtendsClause clause = targetClass.getExtendsClause();
        if (clause == null) {
          clause = (GrExtendsClause)targetClass.addAfter(elementFactory.createExtendsClause(), targetClass.getNameIdentifierGroovy());
        }
        ref = (GrCodeReferenceElement)clause.add(ref);
      }
      if (psiClass.hasTypeParameters() || includeClassName) {
        final Editor editor = CodeInsightUtil.positionCursor(project, targetClass.getContainingFile(), targetClass.getLBrace());
        final TemplateBuilderImpl templateBuilder = editor == null || ApplicationManager.getApplication().isUnitTestMode() ? null
                                                    : (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().createTemplateBuilder(targetClass);

        if (includeClassName && templateBuilder != null) {
          templateBuilder.replaceElement(targetClass.getNameIdentifier(), targetClass.getName());
        }

        if (oldTypeParameterList != null) {
          for (PsiTypeParameter parameter : oldTypeParameterList.getTypeParameters()) {
            final PsiElement param = ref.getTypeArgumentList().add(elementFactory.createTypeElement(jfactory.createType(parameter)));
            if (templateBuilder != null) {
              templateBuilder.replaceElement(param, param.getText());
            }
          }
        }

        final GrTypeParameterList typeParameterList = targetClass.getTypeParameterList();
        assert typeParameterList != null;
        typeParameterList.replace(oldTypeParameterList);

        if (templateBuilder != null) {
          templateBuilder.setEndVariableBefore(ref);
          final Template template = templateBuilder.buildTemplate();
          template.addEndVariable();

          final PsiFile containingFile = targetClass.getContainingFile();

          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());

          final TextRange textRange = targetClass.getTextRange();
          final int startClassOffset = textRange.getStartOffset();
          editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
          CreateFromUsageBaseFix.startTemplate(editor, template, project, new TemplateEditingAdapter() {
            @Override
            public void templateFinished(Template template, boolean brokenOff) {
              chooseAndImplement(psiClass, project,PsiTreeUtil.getParentOfType(containingFile.findElementAt(startClassOffset), GrTypeDefinition.class),editor);
            }
          }, getTitle(psiClass));
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
