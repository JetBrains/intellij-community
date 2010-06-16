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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.inspections.GroovyImmutableAnnotationInspection;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyBaseElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrModifierListStub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public class GrModifierListImpl extends GroovyBaseElementImpl<GrModifierListStub> implements GrModifierList {
  public static final TObjectIntHashMap<String> NAME_TO_MODIFIER_FLAG_MAP = new TObjectIntHashMap<String>();

  static {
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.PUBLIC, GrModifierFlags.PUBLIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.PROTECTED, GrModifierFlags.PROTECTED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.PRIVATE, GrModifierFlags.PRIVATE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.PACKAGE_LOCAL, GrModifierFlags.PACKAGE_LOCAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.STATIC, GrModifierFlags.STATIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.ABSTRACT, GrModifierFlags.ABSTRACT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.FINAL, GrModifierFlags.FINAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.NATIVE, GrModifierFlags.NATIVE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.SYNCHRONIZED, GrModifierFlags.SYNCHRONIZED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.STRICTFP, GrModifierFlags.STRICTFP_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.TRANSIENT, GrModifierFlags.TRANSIENT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.VOLATILE, GrModifierFlags.VOLATILE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.DEF, GrModifierFlags.DEF_MASK);
  }

  public GrModifierListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrModifierListImpl(GrModifierListStub stub) {
    this(stub, GroovyElementTypes.MODIFIERS);
  }

  public GrModifierListImpl(GrModifierListStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitModifierList(this);
  }

  public String toString() {
    return "Modifiers";
  }

  @NotNull
  public PsiElement[] getModifiers() {
    List<PsiElement> modifiers = new ArrayList<PsiElement>();
    PsiElement[] modifiersKeywords = findChildrenByType(TokenSets.MODIFIERS, PsiElement.class);
    GrAnnotation[] modifiersAnnotations = findChildrenByClass(GrAnnotation.class);

    if (modifiersKeywords.length != 0) modifiers.addAll(Arrays.asList(modifiersKeywords));

    if (modifiersAnnotations.length != 0) modifiers.addAll(Arrays.asList(modifiersAnnotations));

    return modifiers.toArray(new PsiElement[modifiers.size()]);
  }

  public boolean hasExplicitVisibilityModifiers() {
    final GrModifierListStub stub = getStub();
    if (stub != null) {
      return (stub.getModifiersFlags() & (GrModifierFlags.PUBLIC_MASK | GrModifierFlags.PROTECTED_MASK | GrModifierFlags.PRIVATE_MASK)) != 0;
    }

    return findChildByType(TokenSets.VISIBILITY_MODIFIERS) != null;
  }

  public boolean hasModifierProperty(@NotNull @NonNls String modifier) {
    final PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclaration &&
        parent.getParent() instanceof GrTypeDefinitionBody) {
      PsiElement pParent = parent.getParent().getParent();
      if (!hasExplicitVisibilityModifiers()) { //properties are backed by private fields
        if (!(pParent instanceof PsiClass) || !((PsiClass)pParent).isInterface()) {
          if (modifier.equals(GrModifier.PRIVATE)) return true;
          if (modifier.equals(GrModifier.PROTECTED)) return false;
          if (modifier.equals(GrModifier.PUBLIC)) return false;
        }
        else {
          if (modifier.equals(GrModifier.STATIC)) return true;
          if (modifier.equals(GrModifier.FINAL)) return true;
        }
      }
      if (pParent instanceof GrTypeDefinition) {
        PsiModifierList modifierList = ((GrTypeDefinition)pParent).getModifierList();
        if (modifierList != null && modifierList.findAnnotation(GroovyImmutableAnnotationInspection.IMMUTABLE) != null) {
          if (modifier.equals(GrModifier.FINAL)) return true;
        }
      }
    }

    if (hasExplicitModifier(modifier)) {
      return true;
    }

    if (modifier.equals(GrModifier.PUBLIC)) {
      //groovy type definitions and methods are public by default
      return !hasExplicitModifier(GrModifier.PRIVATE) && !hasExplicitModifier(GrModifier.PROTECTED);
    }

    if (parent instanceof GrTypeDefinition && modifier.equals(GrModifier.ABSTRACT)) {
      return ((GrTypeDefinition)parent).isInterface();
    }

    return false;
  }

  public boolean hasExplicitModifier(@NotNull @NonNls String name) {
    final GrModifierListStub stub = getStub();
    if (stub != null) {
      final int flag = NAME_TO_MODIFIER_FLAG_MAP.get(name);
      return (stub.getModifiersFlags() & flag) != 0;
    }

    if (name.equals(GrModifier.PUBLIC)) return findChildByType(GroovyElementTypes.kPUBLIC) != null;
    if (name.equals(GrModifier.ABSTRACT)) return findChildByType(GroovyElementTypes.kABSTRACT) != null;
    if (name.equals(GrModifier.NATIVE)) return findChildByType(GroovyElementTypes.kNATIVE) != null;
    if (name.equals(GrModifier.PRIVATE)) return findChildByType(GroovyElementTypes.kPRIVATE) != null;
    if (name.equals(GrModifier.PROTECTED)) return findChildByType(GroovyElementTypes.kPROTECTED) != null;
    if (name.equals(GrModifier.SYNCHRONIZED)) return findChildByType(GroovyElementTypes.kSYNCHRONIZED) != null;
    if (name.equals(GrModifier.STRICTFP)) return findChildByType(GroovyElementTypes.kSTRICTFP) != null;
    if (name.equals(GrModifier.STATIC)) return findChildByType(GroovyElementTypes.kSTATIC) != null;
    if (name.equals(GrModifier.FINAL)) return findChildByType(GroovyElementTypes.kFINAL) != null;
    if (name.equals(GrModifier.TRANSIENT)) return findChildByType(GroovyElementTypes.kTRANSIENT) != null;
    if (name.equals(GrModifier.NATIVE)) return findChildByType(GroovyElementTypes.kNATIVE) != null;
    if (name.equals(GrModifier.DEF)) return findChildByType(GroovyTokenTypes.kDEF) != null;
    return name.equals(GrModifier.VOLATILE) && findChildByType(GroovyElementTypes.kVOLATILE) != null;
  }

  public void setModifierProperty(@NotNull @NonNls String name, boolean doSet) throws IncorrectOperationException {
    if (hasModifierProperty(name) == doSet) return;

    if (doSet) {
      if (GrModifier.PRIVATE.equals(name) ||
          GrModifier.PROTECTED.equals(name) ||
          GrModifier.PUBLIC.equals(name) ||
          GrModifier.PACKAGE_LOCAL.equals(name)) {
        setModifierPropertyInternal(GrModifier.PUBLIC, false);
        setModifierPropertyInternal(GrModifier.PROTECTED, false);
        setModifierPropertyInternal(GrModifier.PRIVATE, false);
      }
    }
    if (GrModifier.PACKAGE_LOCAL.equals(name) /*|| GrModifier.PUBLIC.equals(name)*/) {
      if (getModifiers().length == 0) {
        setModifierProperty(GrModifier.DEF, true);
      }
    }
    else {
      setModifierPropertyInternal(name, doSet);
    }
  }

  private void setModifierPropertyInternal(String name, boolean doSet) {
    if (doSet) {
      final ASTNode modifierNode = GroovyPsiElementFactory.getInstance(getProject()).createModifierFromText(name).getNode();
      addInternal(modifierNode, modifierNode, null, null);
    }
    else {
      final PsiElement[] modifiers = findChildrenByType(TokenSets.MODIFIERS, PsiElement.class);
      for (PsiElement modifier : modifiers) {
        if (name.equals(modifier.getText())) {
          deleteChildRange(modifier, modifier);
          break;
        }
      }
    }
  }

  public void checkSetModifierProperty(@NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
  }

  @NotNull
  public GrAnnotation[] getAnnotations() {
    return findChildrenByClass(GrAnnotation.class);
  }

  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Nullable
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    PsiAnnotation[] annotations = getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      if (qualifiedName.equals(annotation.getQualifiedName())) return annotation;
    }

    return null;
  }

  @NotNull
  public GrAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    final PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(qualifiedName, getResolveScope());
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    if (psiClass != null && psiClass.isAnnotationType()) {
      final GrAnnotation annotation = (GrAnnotation)addAfter(factory.createModifierFromText("@xxx"), null);
      annotation.getClassReference().bindToElement(psiClass);
      return annotation;
    }

    return (GrAnnotation)addAfter(factory.createModifierFromText("@" + qualifiedName), null);
  }
}
