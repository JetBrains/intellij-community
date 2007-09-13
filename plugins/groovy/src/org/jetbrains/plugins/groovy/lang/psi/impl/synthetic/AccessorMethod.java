package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.PomMethod;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class AccessorMethod extends LightElement implements PsiMethod {
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.AccessorMethod");
  private GrField myProperty;

  private boolean myIsSetter;

  private LightReferenceList myThrowsList;


  public AccessorMethod(GrField property, boolean isSetter) {
    super(property.getManager());
    myProperty = property;
    myIsSetter = isSetter;
    myThrowsList = new LightReferenceList(property.getManager());
  }

  @Nullable
  public PsiType getReturnType() {
    if (myIsSetter) return PsiType.VOID;
    return myProperty.getType();
  }

  @Nullable
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  public PsiParameterList getParameterList() {
    final PsiManager manager = getManager();
    return new LightParameterList(manager, new Computable<LightParameter[]>() {
      public LightParameter[] compute() {
        if (myIsSetter) {
          return new LightParameter[]{new LightParameter(manager, null, myProperty.getType(), AccessorMethod.this)};
        }

        return LightParameter.EMPTY_ARRAY;
      }
    });
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    return myThrowsList;
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
    return MethodSignatureUtil.createMethodSignature(getName(), getParameterList(), null, PsiSubstitutor.EMPTY);
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
    final PsiModifierList list = myProperty.getModifierList();
    assert list != null;
    return list;
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    if (name.equals(PsiModifier.PUBLIC)) return true;
    if (name.equals(PsiModifier.PRIVATE)) return false;
    if (name.equals(PsiModifier.PROTECTED)) return false;
    return myProperty.hasModifierProperty(name);
  }

  @NotNull
  public String getName() {
    final String propName = myProperty.getName();
    assert propName != null;
    if (myIsSetter) return "set" + StringUtil.capitalize(propName);
    return "get" + StringUtil.capitalize(propName);
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    //do nothing
    return null;
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  public PsiClass getContainingClass() {
    return myProperty.getContainingClass();
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
    return myProperty.getContainingFile();
  }

  public TextRange getTextRange() {
    return null;
  }

  public void navigate(boolean requestFocus) {
    myProperty.navigate(requestFocus);
  }

  public String toString() {
    return "AccessorMethod";
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
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  public GrField getProperty() {
    return myProperty;
  }
}