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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author ven
 */
public class GrAccessorMethodImpl extends LightElement implements GrAccessorMethod {
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrAccessorMethodImpl");
  private GrField myProperty;

  private boolean myIsSetter;

  private LightReferenceList myThrowsList;
  private LightParameterList myParameterList = null;
  private LightModifierList myModifierList = null;
  private String myName;


  public GrAccessorMethodImpl(GrField property, boolean isSetter, String name) {
    super(property.getManager(), GroovyFileType.GROOVY_LANGUAGE);
    myProperty = property;
    myIsSetter = isSetter;
    myThrowsList = new LightReferenceList(property.getManager());
    myName = name;
  }

  @Nullable
  public PsiType getReturnType() {
    if (myIsSetter) return PsiType.VOID;
    return myProperty.getType();
  }

  @Nullable
  public PsiType getReturnTypeGroovy() {
    if (myIsSetter) return PsiType.VOID;
    return myProperty.getTypeGroovy();
  }

  @Nullable
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  public PsiParameterList getParameterList() {
    final PsiManager manager = getManager();
    if (myParameterList == null) {
      myParameterList = new LightParameterList(manager, new Computable<LightParameter[]>() {
        public LightParameter[] compute() {
          if (myIsSetter) {
            PsiType type = myProperty.getDeclaredType();
            if (type == null) {
              type = TypesUtil.getJavaLangObject(myProperty);
            }
            return new LightParameter[]{new LightParameter(manager, myProperty.getName(), null, type, GrAccessorMethodImpl.this)};
          }

          return LightParameter.EMPTY_ARRAY;
        }
      });
    }
    return myParameterList;
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
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return myProperty.getNameIdentifier();
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

  @NotNull
  public PsiModifierList getModifierList() {
    if (myModifierList == null) {
      LinkedHashSet<String> modifiers = new LinkedHashSet<String>();
      modifiers.add(PsiModifier.PUBLIC);
      final PsiModifierList original = myProperty.getModifierList();
      assert original != null;
      if (original.hasExplicitModifier(PsiModifier.STATIC)) modifiers.add(PsiModifier.STATIC);
      if (original.hasExplicitModifier(PsiModifier.ABSTRACT)) modifiers.add(PsiModifier.ABSTRACT);
      if (original.hasExplicitModifier(PsiModifier.FINAL)) modifiers.add(PsiModifier.FINAL);
      myModifierList = new LightModifierList(getManager(), modifiers);
    }

    return myModifierList;
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    if (name.equals(PsiModifier.PUBLIC)) return true;
    if (name.equals(PsiModifier.PRIVATE)) return false;
    if (name.equals(PsiModifier.PROTECTED)) return false;
    return myProperty.hasModifierProperty(name);
  }

  @NotNull
  public String getName() {
    return myName;
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

  public int getTextOffset() {
    return myProperty.getTextOffset();
  }

  public PsiElement copy() {
    return null;
  }

  @NotNull
  public Language getLanguage() {
    return GroovyFileType.GROOVY_LANGUAGE;
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

  public boolean isValid() {
    return myProperty.isValid();
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  public GrField getProperty() {
    return myProperty;
  }
}