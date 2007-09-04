package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.05.2007
 */
public class GrConstructorInvocationImpl extends GroovyPsiElementImpl implements GrConstructorInvocation {
  public GrConstructorInvocationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitConstructorInvocation(this);
  }

  public String toString() {
    return "Constructor invocation";
  }

  @Nullable
  public GrArgumentList getArgumentList() {
    return findChildByClass(GrArgumentList.class);
  }

  public boolean isSuperCall() {
    return findChildByType(GroovyTokenTypes.kSUPER) != null;
  }

  public boolean isThisCall() {
    return findChildByType(GroovyTokenTypes.kTHIS) != null;
  }

  private static final TokenSet THIS_OR_SUPER_SET = TokenSet.create(GroovyTokenTypes.kTHIS, GroovyTokenTypes.kSUPER);

  public PsiElement getThisOrSuperKeyword() {
    return findChildByType(THIS_OR_SUPER_SET);
  }

  public GroovyResolveResult[] multiResolveConstructor() {
    PsiClass clazz = getDelegatedClass();
    if (clazz != null) {
      PsiType[] argTypes = PsiUtil.getArgumentTypes(getFirstChild(), false);
      MethodResolverProcessor processor = new MethodResolverProcessor(clazz.getName(), this, false, true, argTypes);
      clazz.processDeclarations(processor, PsiSubstitutor.EMPTY, null, this);
      return processor.getCandidates();
    }
    return null;
  }

  public PsiMethod resolveConstructor() {
    return PsiImplUtil.extractUniqueResult(multiResolveConstructor());
  }

  public PsiClass getDelegatedClass() {
    GrTypeDefinition typeDefinition = PsiTreeUtil.getParentOfType(this, GrTypeDefinition.class);
    if (typeDefinition != null) {
      return isThisCall() ? typeDefinition : typeDefinition.getSuperClass();
    }
    return null;
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getThisOrSuperKeyword().getTextLength());
  }

  @Nullable
  public PsiElement resolve() {
    return resolveConstructor();
  }

  public String getCanonicalText() {
    return getText(); //TODO
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return this;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return this;
  }

  public boolean isReferenceTo(PsiElement element) {
    return element instanceof PsiMethod &&
        ((PsiMethod) element).isConstructor() &&
        getManager().areElementsEquivalent(element, resolve());

  }

  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }
}
