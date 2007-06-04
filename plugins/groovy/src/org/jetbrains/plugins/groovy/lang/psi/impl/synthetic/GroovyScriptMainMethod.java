package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.PomMethod;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class GroovyScriptMainMethod extends LightElement implements PsiMethod {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptMainMethod");
  public PsiMethod myCodeBehindMethod;
  private GroovyScriptClass myScriptClass;

  public GroovyScriptMainMethod(GroovyScriptClass scriptClass) {
    super(scriptClass.getManager());
    myScriptClass = scriptClass;
    PsiElementFactory factory = scriptClass.getManager().getElementFactory();
    try {
      myCodeBehindMethod = factory.createMethodFromText("public static final void main(java.lang.String[] args) {}", null);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  public PsiType getReturnType() {
    return PsiType.VOID;
  }

  @Nullable
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return myCodeBehindMethod.getParameterList();
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    return myCodeBehindMethod.getThrowsList();
  }

  @Nullable
  public PsiCodeBlock getBody() {
    return null;
  }

  public boolean isConstructor() {
    return false;
  }

  public boolean isVarArgs() {
    return false;
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return myCodeBehindMethod.getSignature(substitutor);
  }

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return new PsiMethod[0];
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return new PsiMethod[0];
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return new PsiMethod[0];
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return Collections.emptyList();
  }

  @Nullable
  public PsiMethod findDeepestSuperMethod() {
    return null;
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return new PsiMethod[0];
  }

  public PomMethod getPom() {
    return null;
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return myCodeBehindMethod.getModifierList();
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return myCodeBehindMethod.hasModifierProperty(name);
  }

  @NotNull
  public String getName() {
    return "main";
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot set name");
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return myCodeBehindMethod.getHierarchicalMethodSignature();
  }

  public PsiClass getContainingClass() {
    return null;
  }

  @NonNls
  public String getText() {
    return null;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
  }

  public PsiElement copy() {
    return null;
  }

  public PsiFile getContainingFile() {
    return myScriptClass.getContainingFile();
  }

  public TextRange getTextRange() {
    return myScriptClass.getTextRange();
  }

  public String toString() {
    return "GroovyScriptMainMethod";
  }
  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  public boolean hasTypeParameters() {
    return false;
  }

  @Nullable
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return new PsiTypeParameter[0];
  }
}
