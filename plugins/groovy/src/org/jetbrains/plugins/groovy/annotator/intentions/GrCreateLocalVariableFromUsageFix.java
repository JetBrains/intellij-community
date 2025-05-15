// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

public class GrCreateLocalVariableFromUsageFix extends Intention {
  private final GrVariableDeclarationOwner myOwner;
  private final GrReferenceExpression myRefExpression;

  public GrCreateLocalVariableFromUsageFix(GrReferenceExpression refExpression, GrVariableDeclarationOwner owner) {
    myRefExpression = refExpression;
    myOwner = owner;
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("create.variable.from.usage.family.name");
  }

  @Override
  public @NotNull String getText() {
    return GroovyBundle.message("create.variable.from.usage", myRefExpression.getReferenceName());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myOwner.isValid() && myRefExpression.isValid();
  }

  protected static @Nullable Editor positionCursor(Project project, PsiFile targetFile, PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    VirtualFile vFile = targetFile.getVirtualFile();
    assert vFile != null;
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, vFile, textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    TypeConstraint[] constraints = GroovyExpectedTypesProvider.calculateTypeConstraints(myRefExpression);
    PsiType type = constraints.length == 0 || constraints[0].getType().equals(PsiTypes.voidType()) ? JavaPsiFacade.getInstance(project).getElementFactory().createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(project)) : constraints[0].getType();
    GrVariableDeclaration declaration = GroovyPsiElementFactory.getInstance(project)
      .createVariableDeclaration(ArrayUtilRt.EMPTY_STRING_ARRAY, "", type, myRefExpression.getReferenceName());
    return new IntentionPreviewInfo.CustomDiff(GroovyFileType.GROOVY_FILE_TYPE, "", declaration.getText());
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final PsiFile file = element.getContainingFile();
    PsiClassType type = JavaPsiFacade.getInstance(project).getElementFactory().createTypeByFQClassName("Object", GlobalSearchScope.allScope(project));
    GrVariableDeclaration decl = GroovyPsiElementFactory.getInstance(project).createVariableDeclaration(ArrayUtilRt.EMPTY_STRING_ARRAY, "", type, myRefExpression.getReferenceName());
    int offset = myRefExpression.getTextRange().getStartOffset();
    GrStatement anchor = findAnchor(file, offset);

    TypeConstraint[] constraints = GroovyExpectedTypesProvider.calculateTypeConstraints(myRefExpression);
    if (myRefExpression.equals(anchor)) {
      decl = myRefExpression.replaceWithStatement(decl);
    }
    else {
      decl = myOwner.addVariableDeclarationBefore(decl, anchor);
    }
    GrTypeElement typeElement = decl.getTypeElementGroovy();
    assert typeElement != null;
    ChooseTypeExpression expr = new ChooseTypeExpression(constraints, PsiManager.getInstance(project), typeElement.getResolveScope());
    TemplateBuilderImpl builder = new TemplateBuilderImpl(decl);
    builder.replaceElement(typeElement, expr);
    decl = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(decl);
    Template template = builder.buildTemplate();

    Editor newEditor = positionCursor(project, myOwner.getContainingFile(), decl);
    TextRange range = decl.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);
    manager.startTemplate(newEditor, template);

  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return myRefExpression.isValid() && myOwner.isValid();
      }
    };
  }

  private @Nullable GrStatement findAnchor(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null && offset > 0) element = file.findElementAt(offset - 1);
    while (element != null) {
      if (myOwner.equals(element.getParent())) return element instanceof GrStatement ? (GrStatement)element : null;
      element = element.getParent();
    }
    return null;
  }
}
