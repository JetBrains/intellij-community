/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import com.intellij.lang.ASTNode;
import com.intellij.pom.java.PomMethod;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.*;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.JavaIdentifier;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */

public class GrMethodDefinitionImpl extends GroovyPsiElementImpl implements GrMethod {
  public GrMethodDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitMethod(this);
  }

  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  public PsiElement getNameIdentifierGroovy() {
    return findChildByType(GroovyElementTypes.mIDENT);
  }

  public String toString() {
    return "Method";
  }

  @Nullable
  public GrOpenBlock getBlock() {
    return this.findChildByClass(GrOpenBlock.class);
  }

  public GrParameter[] getParameters() {
    GrParameterListImpl parameterList = findChildByClass(GrParameterListImpl.class);
    if (parameterList != null) {
      return parameterList.getParameters();
    }

    return GrParameter.EMPTY_ARRAY;
  }

  public GrTypeElement getReturnTypeElementGroovy() {
    return findChildByClass(GrTypeElement.class);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    for (final GrParameter parameter : getParameters()) {
      if (!ResolveUtil.processElement(processor, parameter)) return false;
    }

    return true;
  }

  public GrMember[] getMembers() {
    return new GrMember[]{this};
  }

  private static class MyTypeCalculator implements Function<GrMethod, PsiType> {

    public PsiType fun(GrMethod method) {
      GrTypeElement element = method.getReturnTypeElementGroovy();
      if (element == null) {
        return GroovyPsiManager.getInstance(method.getProject()).inferType(method, new MethodTypeInferencer(method));
      }
      return element.getType();
    }
  }

  private static MyTypeCalculator ourTypesCalculator = new MyTypeCalculator();

  //PsiMethod implementation
  @Nullable
  public PsiType getReturnType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, ourTypesCalculator);
  }

  @Nullable
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return findChildByClass(GrParameterList.class);
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    return findChildByClass(GrThrowsClause.class);
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
    return new JavaIdentifier(getManager(), getContainingFile(), getNameIdentifierGroovy().getTextRange());
  }

  private void findSuperMethodRecursilvely(Set<PsiMethod> methods, PsiClass psiClass, boolean allowStatic, Set<PsiClass> visited, MethodSignature signature) {
    if (visited.contains(psiClass)) return;
    visited.add(psiClass);
    PsiClassType[] superClassTypes = psiClass.getSuperTypes();

    for (PsiClassType superClassType : superClassTypes) {
      PsiClass resolvedSuperClass = superClassType.resolve();

      if (resolvedSuperClass == null) continue;
      PsiMethod[] superClassMethods = resolvedSuperClass.getMethods();

      for (PsiMethod superClassMethod : superClassMethods) {
        MethodSignature superMethodSignature = superClassMethod.getHierarchicalMethodSignature();

        if (PsiImplUtil.isExtendsSignature(superMethodSignature, signature)) {
          if (allowStatic || !superClassMethod.getModifierList().hasExplicitModifier(PsiModifier.STATIC)) {
            methods.add(superClassMethod);
          }
        }
      }

      findSuperMethodRecursilvely(methods, resolvedSuperClass, allowStatic, visited, signature);
    }
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    PsiClass containingClass = getContainingClass();

    Map<MethodSignature, PsiMethod> methods = new HashMap<MethodSignature, PsiMethod>();
    return findDeepestSuperMethodForClass(methods, containingClass);
  }

  private PsiMethod findDeepestSuperMethodForInterface(PsiMethod[] deepestMethod, PsiClass psiClass) {
    PsiClassType[] superClassTypes = psiClass.getSuperTypes();

    for (PsiClassType superClassType : superClassTypes) {
      PsiClass resolvedSuperClass = superClassType.resolve();

      assert resolvedSuperClass instanceof GrInterfaceDefinition;

      if (resolvedSuperClass == null) continue;
      PsiMethod[] superClassMethods = resolvedSuperClass.getMethods();

      for (PsiMethod superClassMethod : superClassMethods) {
        MethodSignature superMethodSignature = superClassMethod.getHierarchicalMethodSignature();

        if (superMethodSignature.equals(getHierarchicalMethodSignature())) {
          deepestMethod[0] = superClassMethod;
          break;
        }
      }
      findDeepestSuperMethodForInterface(deepestMethod, resolvedSuperClass);
    }

    return deepestMethod[0];
  }

  private PsiMethod[] findDeepestSuperMethodForClass(Map<MethodSignature, PsiMethod> signaturesToMethods, PsiClass psiClass) {
    PsiClassType[] superClassTypes = psiClass.getSuperTypes();
    PsiMethod deepestInterfacesHierarchyMethod = null;

    for (PsiClassType superClassType : superClassTypes) {
      PsiClass resolvedSuperClass = superClassType.resolve();

      if (resolvedSuperClass == null) continue;
      if (resolvedSuperClass.isInterface()) {
        deepestInterfacesHierarchyMethod = findDeepestSuperMethodForInterface(new PsiMethod[1], resolvedSuperClass);
        continue;
      }

      PsiMethod[] superClassMethods = resolvedSuperClass.getMethods();

      for (PsiMethod superClassMethod : superClassMethods) {
        MethodSignature superMethodSignature = superClassMethod.getHierarchicalMethodSignature();

        if (superMethodSignature.equals(getHierarchicalMethodSignature()) && !superClassMethod.getModifierList().hasExplicitModifier(PsiModifier.STATIC)) {
          signaturesToMethods.put(superMethodSignature, superClassMethod);
        }
      }

      findDeepestSuperMethodForClass(signaturesToMethods, resolvedSuperClass);
    }

    List<PsiMethod> values = new ArrayList<PsiMethod>();
    values.addAll(signaturesToMethods.values());

    if (deepestInterfacesHierarchyMethod != null) {
      values.add(deepestInterfacesHierarchyMethod);
    }
    return values.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    PsiClass containingClass = getContainingClass();

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    findSuperMethodRecursilvely(methods, containingClass, false, new HashSet<PsiClass>(), createMethodSignature());

    return methods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    findSuperMethodRecursilvely(methods, parentClass, false, new HashSet<PsiClass>(), createMethodSignature());
    return methods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    PsiClass containingClass = getContainingClass();

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    final MethodSignature signature = createMethodSignature();
    findSuperMethodRecursilvely(methods, containingClass, true, new HashSet<PsiClass>(), signature);

    List<MethodSignatureBackedByPsiMethod> result = new ArrayList<MethodSignatureBackedByPsiMethod>();
    for (PsiMethod method : methods) {
      result.add(method.getHierarchicalMethodSignature());
    }

    return result;
  }

  private MethodSignature createMethodSignature() {
    final GrParameter[] parameters = getParameters();
    PsiType[] types = new PsiType[parameters.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = parameters[i].getType();
    }
    return MethodSignatureUtil.createMethodSignature(getName(), types, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    PsiClass containingClass = getContainingClass();

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    findSuperMethodRecursilvely(methods, containingClass, false, new HashSet<PsiClass>(), createMethodSignature());

    return methods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  /*
  * @deprecated use {@link #findDeepestSuperMethods()} instead
  */

  @Nullable
  public PsiMethod findDeepestSuperMethod() {
    return null;
  }

  public PomMethod getPom() {
    return null;
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return findChildByClass(GrModifierListImpl.class);
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    if (name.equals(PsiModifier.ABSTRACT)) {
      final PsiClass containingClass = getContainingClass();
      if (containingClass != null && containingClass.isInterface()) return true;
    }

    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public String getName() {
    PsiElement nameElement = getNameIdentifierGroovy();
    if (nameElement == null) {
      nameElement = findChildByType(GroovyTokenTypes.mSTRING_LITERAL);
    }
    if (nameElement == null) {
      nameElement = findChildByType(GroovyTokenTypes.mGSTRING_LITERAL);
    }

    assert nameElement != null;
    return nameElement.getText();
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(name, getNameIdentifierGroovy());

    return this;
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

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinitionBody) return (PsiClass) parent.getParent();
    return null;
  }

  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getUseScope(this);
  }
}