// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.base;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix;
import com.intellij.codeInsight.template.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyModifiersUtil;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.template.expressions.ParameterNameExpression;
import org.jetbrains.plugins.groovy.template.expressions.StringParameterNameExpression;

public class IntentionUtils {

  private static final Logger LOG = Logger.getInstance(IntentionUtils.class);

  public static void createTemplateForMethod(ChooseTypeExpression[] paramTypesExpressions,
                                             PsiMethod method,
                                             PsiClass owner,
                                             TypeConstraint[] constraints,
                                             boolean isConstructor,
                                             @NotNull final PsiElement context) {
    ParameterNameExpression[] nameExpressions = new ParameterNameExpression[paramTypesExpressions.length];
    for (int i = 0; i < nameExpressions.length; i++) {
      nameExpressions[i] = StringParameterNameExpression.Companion.getEMPTY();
    }

    ChooseTypeExpression returnTypeExpression = new ChooseTypeExpression(
      constraints,
      owner.getManager(),
      context.getResolveScope(),
      method.getLanguage() == GroovyLanguage.INSTANCE
    );
    createTemplateForMethod(paramTypesExpressions, nameExpressions, method, owner, returnTypeExpression, isConstructor, context);
  }

  public static void createTemplateForMethod(ChooseTypeExpression[] paramTypesExpressions,
                                             ParameterNameExpression[] paramNameExpressions,
                                             PsiMethod method,
                                             PsiClass owner,
                                             ChooseTypeExpression returnTypeExpression,
                                             boolean isConstructor,
                                             @Nullable final PsiElement context) {

    final Project project = owner.getProject();
    PsiTypeElement typeElement = method.getReturnTypeElement();

    TemplateBuilderImpl builder = new TemplateBuilderImpl(method);
    if (!isConstructor) {
      assert typeElement != null;
      builder.replaceElement(typeElement, returnTypeExpression);
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiTypeElement parameterTypeElement = parameter.getTypeElement();
      builder.replaceElement(parameterTypeElement, paramTypesExpressions[i]);
      builder.replaceElement(parameter.getNameIdentifier(), paramNameExpressions[i]);
    }

    PsiCodeBlock body = method.getBody();
    if (body != null) {
      PsiElement lbrace = body.getLBrace();
      assert lbrace != null;
      builder.setEndVariableAfter(lbrace);
    }
    else {
      builder.setEndVariableAfter(method.getParameterList());
    }

    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);
    Template template = builder.buildTemplate();

    final PsiFile targetFile = owner.getContainingFile();
    final Editor newEditor = positionCursor(project, targetFile, method);
    TextRange range = method.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);


    TemplateEditingListener templateListener = new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
          final int offset = newEditor.getCaretModel().getOffset();
          PsiMethod method1 = PsiTreeUtil.findElementOfClassAtOffset(targetFile, offset - 1, PsiMethod.class, false);
          if (context instanceof PsiMethod) {
            final PsiTypeParameter[] typeParameters = ((PsiMethod)context).getTypeParameters();
            if (typeParameters.length > 0) {
              for (PsiTypeParameter typeParameter : typeParameters) {
                if (CreateMethodFromUsageFix.checkTypeParam(method1, typeParameter)) {
                  final JVMElementFactory factory = JVMElementFactories.getFactory(method1.getLanguage(), method1.getProject());
                  PsiTypeParameterList list = method1.getTypeParameterList();
                  if (list == null) {
                    PsiTypeParameterList newList = factory.createTypeParameterList();
                    list = (PsiTypeParameterList)method1.addAfter(newList, method1.getModifierList());
                  }
                  list.add(factory.createTypeParameter(typeParameter.getName(), typeParameter.getExtendsList().getReferencedTypes()));
                }
              }
            }
          }
          if (method1 != null) {
            try {
              final boolean hasNoReturnType = method1.getReturnTypeElement() == null && method1 instanceof GrMethod;
              if (hasNoReturnType) {
                ((GrMethod)method1).setReturnType(PsiType.VOID);
              }
              if (method1.getBody() != null) {
                FileTemplateManager templateManager = FileTemplateManager.getInstance(project);
                FileTemplate fileTemplate = templateManager.getCodeTemplate(GroovyTemplates.GROOVY_FROM_USAGE_METHOD_BODY);

                PsiClass containingClass = method1.getContainingClass();
                LOG.assertTrue(!containingClass.isInterface() || GrTraitUtil.isTrait(containingClass), "Interface bodies should be already set up");
                CreateFromUsageUtils.setupMethodBody(method1, containingClass, fileTemplate);
              }
              if (hasNoReturnType) {
                ((GrMethod)method1).setReturnType(null);
              }

              if (method1 instanceof GrMethod && GroovyModifiersUtil.isDefUnnecessary((GrMethod)method1)) {
                ((GrMethod)method1).getModifierList().setModifierProperty(GrModifier.DEF, false);
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }

            CreateFromUsageUtils.setupEditor(method1, newEditor);
          }
        });
      }
    };
    manager.startTemplate(newEditor, template, templateListener);
  }

  public static Editor positionCursor(@NotNull Project project, @NotNull PsiFile targetFile, @NotNull PsiElement element) {
    int textOffset = element.getTextOffset();
    VirtualFile virtualFile = targetFile.getVirtualFile();
    if (virtualFile != null) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, textOffset);
      return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
    else {
      return null;
    }
  }
}
