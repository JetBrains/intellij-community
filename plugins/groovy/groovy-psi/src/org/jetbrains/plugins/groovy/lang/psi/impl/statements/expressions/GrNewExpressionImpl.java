// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GrArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrArrayDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrNewExpressionReference;

public class GrNewExpressionImpl extends GrCallExpressionImpl implements GrNewExpression {

  private final GroovyConstructorReference myConstructorReference = new GrNewExpressionReference(this);

  public GrNewExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "NEW expression";
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitNewExpression(this);
  }

  @Override
  public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
    final GrArgumentList list = getArgumentList();
    if (list == null) { //so it is not anonymous class declaration
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      final GrArgumentList newList = factory.createExpressionArgumentList();
      final PsiElement anchor = findAnchor(this);
      final ASTNode anchorNode = anchor.getNode();
      assert anchorNode != null;
      getNode().addChild(newList.getNode(), anchorNode);
    }
    return super.addNamedArgument(namedArgument);
  }

  private static @NotNull PsiElement findAnchor(@NotNull PsiElement element) {
    PsiElement last = element.getLastChild();
    assert last != null;
    while (true) {
      PsiElement prevSibling = last.getPrevSibling();
      if (prevSibling instanceof PsiWhiteSpace || prevSibling instanceof PsiErrorElement) {
        last = prevSibling;
      }
      else {
        break;
      }
    }
    return last;
  }

  @Override
  public @Nullable GrArgumentList getArgumentList() {
    final GrAnonymousClassDefinition anonymous = getAnonymousClassDefinition();
    if (anonymous != null) return anonymous.getArgumentListGroovy();
    return super.getArgumentList();
  }

  @Override
  public GrCodeReferenceElement getReferenceElement() {
    final GrAnonymousClassDefinition anonymous = getAnonymousClassDefinition();
    if (anonymous != null) return anonymous.getBaseClassReferenceGroovy();
    return findChildByClass(GrCodeReferenceElement.class);
  }

  @Override
  public GroovyResolveResult[] multiResolveClass() {
    final GrCodeReferenceElement referenceElement = getReferenceElement();
    if (referenceElement != null) {
      return referenceElement.multiResolve(false);
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  @Override
  public int getArrayCount() {
    final GrArrayDeclaration arrayDeclaration = getArrayDeclaration();
    if (arrayDeclaration == null) return 0;
    return arrayDeclaration.getArrayCount();
  }

  @Override
  public GrAnonymousClassDefinition getAnonymousClassDefinition() {
    return findChildByClass(GrAnonymousClassDefinition.class);
  }

  @Override
  public @Nullable GrArrayDeclaration getArrayDeclaration() {
    return findChildByClass(GrArrayDeclaration.class);
  }

  @Override
  public @Nullable GrArrayInitializer getArrayInitializer() {
    return findChildByClass(GrArrayInitializer.class);
  }

  @Override
  public @Nullable GrTypeArgumentList getConstructorTypeArguments() {
    return findChildByClass(GrTypeArgumentList.class);
  }

  @Override
  public GroovyResolveResult @NotNull [] getCallVariants(@Nullable GrExpression upToArgument) {
    return multiResolve(true);
  }

  @Override
  public GrTypeElement getTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  @Override
  public GroovyResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    GroovyReference reference = getConstructorReference();
    return reference == null ? GroovyResolveResult.EMPTY_ARRAY : reference.multiResolve(incompleteCode);
  }

  @Override
  public @Nullable GroovyConstructorReference getConstructorReference() {
    return getArrayCount() > 0 || getReferenceElement() == null ? null : myConstructorReference;
  }
}
