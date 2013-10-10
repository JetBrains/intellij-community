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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.completion.closureParameters.ClosureParameterInfo;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.template.expressions.ParameterNameExpression;

import java.util.List;

/**
 * @author Max Medvedev
 */
public abstract class ClosureCompleter {
  private static final ExtensionPointName<ClosureCompleter> EP_NAME = ExtensionPointName.create("org.intellij.groovy.closureCompleter");

  @Nullable
  protected abstract List<ClosureParameterInfo> getParameterInfos(InsertionContext context,
                                                                  PsiMethod method,
                                                                  PsiSubstitutor substitutor,
                                                                  PsiElement place);

  public static boolean runClosureCompletion(InsertionContext context,
                                             PsiMethod method,
                                             PsiSubstitutor substitutor,
                                             Document document,
                                             int offset,
                                             PsiElement parent) {
    for (ClosureCompleter completer : EP_NAME.getExtensions()) {
      final List<ClosureParameterInfo> parameterInfos = completer.getParameterInfos(context, method, substitutor, parent);
      if (parameterInfos != null) {
        runClosureTemplate(context, document, offset, substitutor, method, parameterInfos);
        return true;
      }
    }

    return false;
  }

  private static boolean runClosureTemplate(InsertionContext context,
                                            Document document,
                                            int offset,
                                            PsiSubstitutor substitutor,
                                            PsiMethod method,
                                            final List<ClosureParameterInfo> parameters) {
    document.insertString(offset, "{\n}");
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
    final GrClosableBlock closure =
      PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), offset + 1, GrClosableBlock.class, false);
    if (closure == null) return false;

    runTemplate(parameters, closure, substitutor, method, context.getProject(), context.getEditor());
    return true;
  }

  public static void runTemplate(List<ClosureParameterInfo> parameters,
                                 GrClosableBlock block,
                                 PsiSubstitutor substitutor,
                                 PsiMethod method, final Project project,
                                 final Editor editor) {
    if (method instanceof ClsMethodImpl) method = ((ClsMethodImpl)method).getSourceMirrorMethod();

    assert block.getArrow() == null;
    if (parameters.isEmpty()) return;

    StringBuilder buffer = new StringBuilder();
    buffer.append("{");

    List<PsiType> paramTypes = ContainerUtil.newArrayList();
    for (ClosureParameterInfo parameter : parameters) {
      final String type = parameter.getType();
      final String name = parameter.getName();
      if (type != null) {
        final PsiType fromText = JavaPsiFacade.getElementFactory(project).createTypeFromText(type, method);
        final PsiType substituted = substitutor.substitute(fromText);
        paramTypes.add(substituted);
        buffer.append(substituted.getCanonicalText()).append(" ");
      }
      else {
        buffer.append("def ");
      }
      buffer.append(name);
      buffer.append(", ");
    }
    buffer.replace(buffer.length() - 2, buffer.length(), " ->}");

    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    assert file != null;

    final GrClosableBlock closure = GroovyPsiElementFactory.getInstance(project).createClosureFromText(buffer.toString());
    final GrClosableBlock templateClosure = (GrClosableBlock)block.replaceWithExpression(closure, false);

    final TemplateBuilderImpl builder = new TemplateBuilderImpl(templateClosure);

    int i = 0;
    for (GrParameter p : templateClosure.getParameters()) {
      final GrTypeElement typeElement = p.getTypeElementGroovy();
      final PsiElement nameIdentifier = p.getNameIdentifierGroovy();

      if (typeElement != null) {
        final TypeConstraint[] typeConstraints = {SupertypeConstraint.create(paramTypes.get(i++))};
        final ChooseTypeExpression expression = new ChooseTypeExpression(typeConstraints, PsiManager.getInstance(project), nameIdentifier.getResolveScope());
        builder.replaceElement(typeElement, expression);
      }
      else {
        final ChooseTypeExpression expression =
          new ChooseTypeExpression(TypeConstraint.EMPTY_ARRAY, PsiManager.getInstance(project), nameIdentifier.getResolveScope());
        builder.replaceElement(p.getModifierList(), expression);
      }

      builder.replaceElement(nameIdentifier, new ParameterNameExpression(nameIdentifier.getText()));
    }

    final GrClosableBlock afterPostprocess = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(templateClosure);
    final Template template = builder.buildTemplate();
    TextRange range = afterPostprocess.getTextRange();
    document.deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateEditingListener templateListener = new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            PsiDocumentManager.getInstance(project).commitDocument(document);
            final CaretModel caretModel = editor.getCaretModel();
            final int offset = caretModel.getOffset();
            GrClosableBlock block = PsiTreeUtil.findElementOfClassAtOffset(file, offset - 1, GrClosableBlock.class, false);
            if (block != null) {
              final PsiElement arrow = block.getArrow();
              if (arrow != null) {
                caretModel.moveToOffset(arrow.getTextRange().getEndOffset());
              }

              // fix space before closure lbrace
              final TextRange range = block.getTextRange();
              CodeStyleManager.getInstance(project).reformatRange(block.getParent(), range.getStartOffset() - 1, range.getEndOffset(), true);
            }
          }
        });
      }
    };

    TemplateManager manager = TemplateManager.getInstance(project);
    manager.startTemplate(editor, template, templateListener);
  }

}
