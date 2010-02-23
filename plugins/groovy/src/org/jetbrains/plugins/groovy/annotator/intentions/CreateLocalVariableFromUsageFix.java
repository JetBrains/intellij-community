/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.editor.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;

/**
 * @author ven
 */
public class CreateLocalVariableFromUsageFix implements IntentionAction {
  private final GrVariableDeclarationOwner myOwner;
  private final GrReferenceExpression myRefExpression;

  public CreateLocalVariableFromUsageFix(GrReferenceExpression refExpression, GrVariableDeclarationOwner owner) {
    myRefExpression = refExpression;
    myOwner = owner;
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("create.variable.from.usage", myRefExpression.getReferenceName());
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("create.from.usage.family.name");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myOwner.isValid() && myRefExpression.isValid();
  }

  protected static Editor positionCursor(Project project, PsiFile targetFile, PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    VirtualFile vFile = targetFile.getVirtualFile();
    assert vFile != null;
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, vFile, textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiClassType type = JavaPsiFacade.getInstance(project).getElementFactory().createTypeByFQClassName("Object", GlobalSearchScope.allScope(project));
    GrVariableDeclaration decl = GroovyPsiElementFactory.getInstance(project).createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY,
        null, type, myRefExpression.getReferenceName());
    int offset = myRefExpression.getTextRange().getStartOffset();
    GrStatement anchor = findAnchor(file, offset);

    TypeConstraint[] constraints = GroovyExpectedTypesProvider.calculateTypeConstraints(myRefExpression);
    if (anchor.equals(myRefExpression)) {
      decl = myRefExpression.replaceWithStatement(decl);
    } else {
      decl = myOwner.addVariableDeclarationBefore(decl, anchor);
    }
    GrTypeElement typeElement = decl.getTypeElementGroovy();
    assert typeElement != null;
    ChooseTypeExpression expr = new ChooseTypeExpression(constraints, PsiManager.getInstance(project));
    TemplateBuilderImpl builder = new TemplateBuilderImpl(decl);
    builder.replaceElement(typeElement, expr);
    decl = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(decl);
    Template template = builder.buildTemplate();

    Editor newEditor = positionCursor(project, myOwner.getContainingFile(), decl);
    TextRange range = decl.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);
    manager.startTemplate(newEditor, template);
  }

  private GrStatement findAnchor(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null && offset > 0) element = file.findElementAt(offset - 1);
    while (element != null) {
      if (myOwner.equals(element.getParent())) return element instanceof GrStatement ? (GrStatement) element : null;
      element = element.getParent();
    }
    return null;
  }


  public boolean startInWriteAction() {
    return true;
  }
}
