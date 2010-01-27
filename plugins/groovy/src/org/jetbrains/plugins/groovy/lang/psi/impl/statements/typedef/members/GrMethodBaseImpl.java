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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrNamedArgumentSearchVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyBaseElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;
import java.util.*;

/**
 * @author ilyas
 */
public abstract class GrMethodBaseImpl<T extends NamedStub> extends GroovyBaseElementImpl<T> implements GrMethod {

  protected GrMethodBaseImpl(final T stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public GrMethodBaseImpl(final ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitMethod(this);
  }

  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    return findChildByType(TokenSets.PROPERTY_NAMES);
  }

  @Nullable
  public GrOpenBlock getBlock() {
    return this.findChildByClass(GrOpenBlock.class);
  }

  public void setBlock(GrCodeBlock newBlock) {
    ASTNode newNode = newBlock.getNode().copyElement();
    final GrOpenBlock oldBlock = getBlock();
    if (oldBlock == null) {
      getNode().addChild(newNode);
      return;
    }
    getNode().replaceChild(oldBlock.getNode(), newNode);
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

  @Nullable
  public PsiType getDeclaredReturnType() {
    final GrTypeElement typeElement = getReturnTypeElementGroovy();
    if (typeElement != null) return typeElement.getType();
    return null;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    for (final GrTypeParameter typeParameter : getTypeParameters()) {
      if (!ResolveUtil.processElement(processor, typeParameter)) return false;
    }

    for (final GrParameter parameter : getParameters()) {
      if (!ResolveUtil.processElement(processor, parameter)) return false;
    }

    processor.handleEvent(ResolveUtil.DECLARATION_SCOPE_PASSED, this);

    return true;
  }

  public GrMember[] getMembers() {
    return new GrMember[]{this};
  }

  private static final Function<GrMethod, PsiType> ourTypesCalculator = new NullableFunction<GrMethod, PsiType>() {
    public PsiType fun(GrMethod method) {
      PsiType nominal = getNominalType(method);
      if (nominal != null && nominal.equals(PsiType.VOID)) return nominal;
      PsiType inferred = getInferredType(method);
      if (nominal == null) return inferred;
      if (inferred != null && inferred != PsiType.NULL) {
        if (nominal.isAssignableFrom(inferred)) return inferred;
      }
      return nominal;
    }

    @Nullable
    private PsiType getNominalType(GrMethod method) {
      GrTypeElement element = method.getReturnTypeElementGroovy();
      return element != null ? element.getType() : null;
    }

    @Nullable
    private PsiType getInferredType(GrMethod method) {
      final GrOpenBlock block = method.getBlock();
      if (block == null) return null;

      if (GroovyPsiManager.isTypeBeingInferred(method)) {
        return null;
      }

      return GroovyPsiManager.inferType(method, new MethodTypeInferencer(block));
    }
  };

  //PsiMethod implementation
  @Nullable
  public PsiType getReturnType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, ourTypesCalculator);
  }

  @Override
  public Icon getIcon(int flags) {
    RowIcon baseIcon = createLayeredIcon(GroovyIcons.METHOD, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getMethodPresentation(this);
  }

  @Nullable
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  public GrParameterList getParameterList() {
    GrParameterList parameterList = (GrParameterList)findChildByType(GroovyElementTypes.PARAMETERS_LIST);
    assert parameterList != null;
    return parameterList;
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    GrThrowsClause clause = findChildByClass(GrThrowsClause.class);
    assert clause != null;
    return clause;
  }

  @Nullable
  public PsiCodeBlock getBody() {
    return null;
  }

  public boolean isConstructor() {
    return false;
  }

  public boolean isVarArgs() {
    GrParameter[] parameters = getParameters();
    return parameters.length > 0 && parameters[parameters.length - 1].isVarArgs();
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  private static void findSuperMethodRecursively(Set<PsiMethod> methods,
                                           PsiClass psiClass,
                                           boolean allowStatic,
                                           Set<PsiClass> visited,
                                           MethodSignature signature,
                                           @NotNull Set<MethodSignature> discoveredSupers) {
    if (psiClass == null) return;
    if (visited.contains(psiClass)) return;
    visited.add(psiClass);
    PsiClassType[] superClassTypes = psiClass.getSuperTypes();

    for (PsiClassType superClassType : superClassTypes) {
      PsiClass resolvedSuperClass = superClassType.resolve();

      if (resolvedSuperClass == null) continue;
      PsiMethod[] superClassMethods = resolvedSuperClass.getMethods();
      final HashSet<MethodSignature> supers = new HashSet<MethodSignature>(3);

      for (PsiMethod superClassMethod : superClassMethods) {
        MethodSignature superMethodSignature = createMethodSignature(superClassMethod);

        if (PsiImplUtil.isExtendsSignature(superMethodSignature, signature) && !dominated(superMethodSignature, discoveredSupers)) {
          if (allowStatic || !superClassMethod.getModifierList().hasExplicitModifier(PsiModifier.STATIC)) {
            methods.add(superClassMethod);
            supers.add(superMethodSignature);
            discoveredSupers.add(superMethodSignature);
          }
        }
      }

      findSuperMethodRecursively(methods, resolvedSuperClass, allowStatic, visited, signature, discoveredSupers);
      discoveredSupers.removeAll(supers);
    }
  }

  private static boolean dominated(MethodSignature signature, Iterable<MethodSignature> supersInInheritor) {
    for (MethodSignature sig1 : supersInInheritor) {
      if (PsiImplUtil.isExtendsSignature(signature, sig1)) return true;
    }
    return false;
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    findDeepestSuperMethodsForClass(methods, this);
    return methods.toArray(new PsiMethod[methods.size()]);
  }

  private void findDeepestSuperMethodsForClass(List<PsiMethod> collectedMethods, PsiMethod method) {
    PsiClassType[] superClassTypes = method.getContainingClass().getSuperTypes();

    for (PsiClassType superClassType : superClassTypes) {
      PsiClass resolvedSuperClass = superClassType.resolve();

      if (resolvedSuperClass == null) continue;
      PsiMethod[] superClassMethods = resolvedSuperClass.getMethods();

      for (PsiMethod superClassMethod : superClassMethods) {
        MethodSignature superMethodSignature = superClassMethod.getHierarchicalMethodSignature();
        final HierarchicalMethodSignature thisMethodSignature = getHierarchicalMethodSignature();

        if (superMethodSignature.equals(thisMethodSignature) &&
            !superClassMethod.getModifierList().hasExplicitModifier(PsiModifier.STATIC)) {
          checkForMethodOverriding(collectedMethods, superClassMethod);
        }
        findDeepestSuperMethodsForClass(collectedMethods, superClassMethod);
      }
    }
  }

  private static void checkForMethodOverriding(List<PsiMethod> collectedMethods, PsiMethod superClassMethod) {
    int i = 0;
    while (i < collectedMethods.size()) {
      PsiMethod collectedMethod = collectedMethods.get(i);
      if (collectedMethod.getContainingClass().equals(superClassMethod.getContainingClass()) ||
          collectedMethod.getContainingClass().isInheritor(superClassMethod.getContainingClass(), true)) {
        collectedMethods.remove(collectedMethod);
        continue;
      }
      i++;
    }
    collectedMethods.add(superClassMethod);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);

    /*PsiClass containingClass = getContainingClass();

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    findSuperMethodRecursively(methods, containingClass, false, new HashSet<PsiClass>(), createMethodSignature(this),
                                new HashSet<MethodSignature>());

    return methods.toArray(new PsiMethod[methods.size()]);*/
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
    /*Set<PsiMethod> methods = new HashSet<PsiMethod>();
    findSuperMethodRecursively(methods, parentClass, false, new HashSet<PsiClass>(), createMethodSignature(this),
                                new HashSet<MethodSignature>());
    return methods.toArray(new PsiMethod[methods.size()]);*/
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
    /*PsiClass containingClass = getContainingClass();

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    final MethodSignature signature = createMethodSignature(this);
    findSuperMethodRecursively(methods, containingClass, true, new HashSet<PsiClass>(), signature, new HashSet<MethodSignature>());

    List<MethodSignatureBackedByPsiMethod> result = new ArrayList<MethodSignatureBackedByPsiMethod>();
    for (PsiMethod method : methods) {
      result.add(method.getHierarchicalMethodSignature());
    }

    return result;*/
  }

  public static MethodSignature createMethodSignature(PsiMethod method) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiType[] types = new PsiType[parameters.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = parameters[i].getType();
    }
    return MethodSignatureUtil.createMethodSignature(method.getName(), types, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
    /*PsiClass containingClass = getContainingClass();
    if (containingClass == null) return PsiMethod.EMPTY_ARRAY;

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    findSuperMethodRecursively(methods, containingClass, false, new HashSet<PsiClass>(), createMethodSignature(this),
                                new HashSet<MethodSignature>());

    return methods.toArray(new PsiMethod[methods.size()]);*/
  }

  /*
  * @deprecated use {@link #findDeepestSuperMethods()} instead
  */

  @Nullable
  public PsiMethod findDeepestSuperMethod() {
    return null;
  }

  @NotNull
  public GrModifierList getModifierList() {
    GrModifierListImpl list = findChildByClass(GrModifierListImpl.class);
    assert list != null;
    return list;
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
    return PsiImplUtil.getName(this);
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
    return getTypeParameters().length > 0;
  }

  @Nullable
  public GrTypeParameterList getTypeParameterList() {
    return findChildByClass(GrTypeParameterList.class);
  }

  @NotNull
  public GrTypeParameter[] getTypeParameters() {
    final GrTypeParameterList list = getTypeParameterList();
    if (list != null) {
      return list.getTypeParameters();
    }

    return GrTypeParameter.EMPTY_ARRAY;
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass)pparent;
      }
    }


    final PsiFile file = getContainingFile();
    if (file instanceof GroovyFileBase) {
      return ((GroovyFileBase)file).getScriptClass();
    }

    return null;
  }

  @Nullable
  public GrDocComment getDocComment() {
    return GrDocCommentUtil.findDocComment(this);
  }

  public boolean isDeprecated() {
    return false;
  }

  @NotNull
  public SearchScope getUseScope() {
    return com.intellij.psi.impl.PsiImplUtil.getMemberUseScope(this);
  }

  public PsiElement getOriginalElement() {
    final PsiClass containingClass = getContainingClass();
    if (containingClass == null) return this;
    PsiClass originalClass = (PsiClass)containingClass.getOriginalElement();
    final PsiMethod originalMethod = originalClass.findMethodBySignature(this, false);
    return originalMethod != null ? originalMethod : this;
  }



  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    if (parent instanceof GroovyFileImpl || parent instanceof GrTypeDefinitionBody) {
      super.delete();
      return;
    }
    throw new IncorrectOperationException("Invalid enclosing type definition");
  }

  @NotNull
  public Set<String>[] getNamedParametersArray() {
    GrOpenBlock body = getBlock();
    if (body == null) return new HashSet[0];

    List<Set<String>> namedParameters = new LinkedList<Set<String>>();
    GrParameter[] parameters = getParameters();
    for (int i = 0, parametersLength = parameters.length; i < parametersLength; i++) {
      GrParameter parameter = parameters[i];
      PsiType type = parameter.getTypeGroovy();
      GrTypeElement typeElement = parameter.getTypeElementGroovy();
      //equalsToText can't be called here because of stub creating

      if (type == null || type.getPresentableText() == null || type.getPresentableText().endsWith("Map") || typeElement == null) {
        PsiElement expression = parameter.getNameIdentifierGroovy();

        final String paramName = expression.getText();
        final HashSet<String> set = new HashSet<String>();
        namedParameters.add(set);

        body.accept(new GrNamedArgumentSearchVisitor(paramName, set));
      }
    }
    return namedParameters.toArray(new HashSet[0]);
  }

  public PsiMethodReceiver getMethodReceiver() {
    return null;
  }
  public PsiType getReturnTypeNoResolve() {
    return getReturnType();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }
}