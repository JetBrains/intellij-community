/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.annotator.intentions.CreateClassActionBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;

/**
 * @author Max Medvedev
 */
public class GrCreateSubclassAction extends CreateSubclassAction {
  private static final Logger LOG = Logger.getInstance(GrCreateSubclassAction.class);

  @Override
  protected boolean isSupportedLanguage(PsiClass aClass) {
    return aClass.getLanguage() == GroovyLanguage.INSTANCE;
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
    final Ref<GrTypeDefinition> targetClass = new Ref<>();

    new WriteCommandAction(project, getTitle(psiClass), getTitle(psiClass)) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

        final GrTypeParameterList oldTypeParameterList = psiClass.getTypeParameterList();

        try {
          targetClass.set(CreateClassActionBase.createClassByType(targetDirectory, className, PsiManager.getInstance(project), psiClass, GroovyTemplates.GROOVY_CLASS, true));
        }
        catch (final IncorrectOperationException e) {
          ApplicationManager.getApplication().invokeLater(
            () -> Messages.showErrorDialog(project, CodeInsightBundle.message("intention.error.cannot.create.class.message", className) +
                                                  "\n" + e.getLocalizedMessage(),
                                         CodeInsightBundle.message("intention.error.cannot.create.class.title")));
          return;
        }
        startTemplate(oldTypeParameterList, project, psiClass, targetClass.get(), false);
      }
    }.execute();

    if (targetClass.get() == null) return null;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !psiClass.hasTypeParameters()) {

      final Editor editor = CodeInsightUtil.positionCursorAtLBrace(project, targetClass.get().getContainingFile(), targetClass.get());
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
    GrCodeReferenceElement stubRef = elementFactory.createCodeReferenceElementFromClass(psiClass);
    try {
      GrReferenceList clause = psiClass.isInterface() ? targetClass.getImplementsClause() : targetClass.getExtendsClause();
      if (clause == null) {
        GrReferenceList stubRefList = psiClass.isInterface() ? elementFactory.createImplementsClause() : elementFactory.createExtendsClause();
        clause = (GrExtendsClause)targetClass.addAfter(stubRefList, targetClass.getNameIdentifierGroovy());
      }
      GrCodeReferenceElement ref = (GrCodeReferenceElement)clause.add(stubRef);

      if (psiClass.hasTypeParameters() || includeClassName) {
        final Editor editor = CodeInsightUtil.positionCursorAtLBrace(project, targetClass.getContainingFile(), targetClass);
        final TemplateBuilderImpl templateBuilder = editor == null || ApplicationManager.getApplication().isUnitTestMode() ? null
                                                    : (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().createTemplateBuilder(targetClass);

        if (includeClassName && templateBuilder != null) {
          templateBuilder.replaceElement(targetClass.getNameIdentifier(), targetClass.getName());
        }

        if (oldTypeParameterList != null && oldTypeParameterList.getTypeParameters().length > 0) {
          GrTypeArgumentList existingList = ref.getTypeArgumentList();
          final GrTypeParameterList typeParameterList =
            (GrTypeParameterList)targetClass.addAfter(elementFactory.createTypeParameterList(), targetClass.getNameIdentifierGroovy());

          GrTypeArgumentList argList;
          if (existingList == null) {
            GrCodeReferenceElement codeRef = elementFactory.createCodeReferenceElementFromText("A<T>");
            argList = ((GrTypeArgumentList)ref.add(codeRef.getTypeArgumentList()));
            argList.getTypeArgumentElements()[0].delete();
          }
          else {
            argList = existingList;
          }

          for (PsiTypeParameter parameter : oldTypeParameterList.getTypeParameters()) {
            final PsiElement param = argList.add(elementFactory.createTypeElement(jfactory.createType(parameter)));
            if (templateBuilder != null) {
              templateBuilder.replaceElement(param, param.getText());
            }

            typeParameterList.add(elementFactory.createTypeParameter(parameter.getName(), parameter.getExtendsListTypes()));
          }
        }

        if (templateBuilder != null) {
          templateBuilder.setEndVariableBefore(ref);
          final Template template = templateBuilder.buildTemplate();
          template.addEndVariable();

          final PsiFile containingFile = targetClass.getContainingFile();

          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());

          final TextRange textRange = targetClass.getTextRange();
          final RangeMarker startClassOffset = editor.getDocument().createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
          startClassOffset.setGreedyToLeft(true);
          startClassOffset.setGreedyToRight(true);
          editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
          CreateFromUsageBaseFix.startTemplate(editor, template, project, new TemplateEditingAdapter() {
            @Override
            public void templateFinished(Template template, boolean brokenOff) {
              chooseAndImplement(psiClass, project,PsiTreeUtil.getParentOfType(containingFile.findElementAt(startClassOffset.getStartOffset()), GrTypeDefinition.class),editor);
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
