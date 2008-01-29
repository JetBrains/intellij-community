/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.editor.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;

/**
 * @author ven
 */
public class CreateFieldFromUsageFix implements IntentionAction {
  private GrMemberOwner myTargetClass;
  private GrReferenceExpression myRefExpression;

  public CreateFieldFromUsageFix(GrReferenceExpression refExpression, GrMemberOwner targetClass) {
    myRefExpression = refExpression;
    myTargetClass = targetClass;
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("create.field.from.usage", myRefExpression.getReferenceName());
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("create.from.usage.family.name");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myTargetClass.isValid() && myRefExpression.isValid();
  }

  protected static Editor positionCursor(Project project, PsiFile targetFile, PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile(), textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiClassType type = PsiManager.getInstance(project).getElementFactory().createTypeByFQClassName("java.lang.Object", GlobalSearchScope.allScope(project));
    GrVariableDeclaration fieldDecl = GroovyPsiElementFactory.getInstance(project).createFieldDeclaration(ArrayUtil.EMPTY_STRING_ARRAY,
        myRefExpression.getReferenceName(), null, type);
    fieldDecl = myTargetClass.addMemberDeclaration(fieldDecl);
    GrTypeElement typeElement = fieldDecl.getTypeElementGroovy();
    assert typeElement != null;
    TypeConstraint[] constraints = GroovyExpectedTypesUtil.calculateTypeConstraints(myRefExpression);
    ChooseTypeExpression expr = new ChooseTypeExpression(constraints, PsiManager.getInstance(project));
    TemplateBuilder builder = new TemplateBuilder(fieldDecl);
    builder.replaceElement(typeElement, expr);
    //builder.setEndVariableAfter(fieldDecl.getVariables()[0].getNameIdentifierGroovy());
    Template template = builder.buildTemplate();

    Editor newEditor = positionCursor(project, myTargetClass.getContainingFile(), fieldDecl);
    TextRange range = fieldDecl.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);
    manager.startTemplate(newEditor, template);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
