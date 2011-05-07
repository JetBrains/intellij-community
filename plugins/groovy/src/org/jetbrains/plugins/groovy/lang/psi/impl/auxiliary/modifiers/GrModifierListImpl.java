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
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayFactory;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrModifierListStub;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
@SuppressWarnings({"StaticFieldReferencedViaSubclass"})
public class GrModifierListImpl extends GrStubElementBase<GrModifierListStub> implements GrModifierList, StubBasedPsiElement<GrModifierListStub> {
  public static final TObjectIntHashMap<String> NAME_TO_MODIFIER_FLAG_MAP = new TObjectIntHashMap<String>();
  private static final ArrayFactory<GrAnnotation> ARRAY_FACTORY = new ArrayFactory<GrAnnotation>() {
    @Override
    public GrAnnotation[] create(int count) {
      return new GrAnnotation[count];
    }
  };

  private static final TObjectIntHashMap<String> PRIORITY = new TObjectIntHashMap<String>(16);

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


    PRIORITY.put(GrModifier.PUBLIC,           0);
    PRIORITY.put(GrModifier.PROTECTED,        0);
    PRIORITY.put(GrModifier.PRIVATE,          0);
    PRIORITY.put(GrModifier.PACKAGE_LOCAL,    0);
    PRIORITY.put(GrModifier.STATIC,           1);
    PRIORITY.put(GrModifier.ABSTRACT,         1);
    PRIORITY.put(GrModifier.FINAL,            2);
    PRIORITY.put(GrModifier.NATIVE,           3);
    PRIORITY.put(GrModifier.SYNCHRONIZED,     3);
    PRIORITY.put(GrModifier.STRICTFP,         3);
    PRIORITY.put(GrModifier.TRANSIENT,        3);
    PRIORITY.put(GrModifier.VOLATILE,         3);
    PRIORITY.put(GrModifier.DEF,              4);
  }

  public GrModifierListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiElement getParent() {
    return getParentByStub();
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
    PsiElement[] modifiersKeywords = findChildrenByType(TokenSets.MODIFIERS, PsiElement.class);
    GrAnnotation[] modifiersAnnotations = findChildrenByClass(GrAnnotation.class);

    if (modifiersAnnotations.length == 0) return modifiersKeywords;

    PsiElement[] res = new PsiElement[modifiersAnnotations.length + modifiersKeywords.length];

    int i = 0;
    for (PsiElement modifiersKeyword : modifiersKeywords) {
      res[i++] = modifiersKeyword;
    }
    for (GrAnnotation modifiersAnnotation : modifiersAnnotations) {
      res[i++] = modifiersAnnotation;
    }

    return res;
  }

  public boolean hasExplicitVisibilityModifiers() {
    final GrModifierListStub stub = getStub();
    if (stub != null) {
      return (stub.getModifiersFlags() & (GrModifierFlags.PUBLIC_MASK | GrModifierFlags.PROTECTED_MASK | GrModifierFlags.PRIVATE_MASK)) != 0;
    }

    return findChildByType(TokenSets.VISIBILITY_MODIFIERS) != null;
  }

  public static boolean checkModifierProperty(@NotNull GrModifierList modifierList, @NotNull String modifier) {
    final PsiElement owner = modifierList.getParent();
    if (owner instanceof GrVariableDeclaration && owner.getParent() instanceof GrTypeDefinitionBody) {
      PsiElement pParent = owner.getParent().getParent();
      if (!modifierList.hasExplicitVisibilityModifiers()) { //properties are backed by private fields
        if (!(pParent instanceof PsiClass) || !((PsiClass)pParent).isInterface()) {
          if (modifier.equals(GrModifier.PRIVATE)) return true;
          if (modifier.equals(GrModifier.PROTECTED)) return false;
          if (modifier.equals(GrModifier.PUBLIC)) return false;
        }
      }

      if (pParent instanceof PsiClass && ((PsiClass)pParent).isInterface()) {
        if (modifier.equals(GrModifier.STATIC)) return true;
        if (modifier.equals(GrModifier.FINAL)) return true;
      }
      if (pParent instanceof GrTypeDefinition) {
        PsiModifierList pModifierList = ((GrTypeDefinition)pParent).getModifierList();
        if (pModifierList != null && pModifierList.findAnnotation(GroovyImmutableAnnotationInspection.IMMUTABLE) != null) {
          if (modifier.equals(GrModifier.FINAL)) return true;
        }
      }
    }

    if (modifierList.hasExplicitModifier(modifier)) {
      return true;
    }

    if (modifier.equals(GrModifier.PUBLIC)) {
      if (owner instanceof GrPackageDefinition) return false;
      if (owner instanceof GrVariableDeclaration && !(owner.getParent() instanceof GrTypeDefinitionBody) || owner instanceof GrVariable) {
        return false;
      }
      //groovy type definitions and methods are public by default
      return !modifierList.hasExplicitModifier(GrModifier.PRIVATE) && !modifierList.hasExplicitModifier(GrModifier.PROTECTED);
    }

    if (owner instanceof GrTypeDefinition) {
      if (modifier.equals(GrModifier.STATIC)) {
        final PsiClass containingClass = ((GrTypeDefinition)owner).getContainingClass();
        return containingClass != null && containingClass.isInterface();
      }
      if (modifier.equals(GrModifier.ABSTRACT)) {
        return ((GrTypeDefinition)owner).isInterface();
      }
    }

    return false;
  }

  public boolean hasModifierProperty(@NotNull @NonNls String modifier) {
    return checkModifierProperty(this, modifier);
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
      PsiElement modifier = GroovyPsiElementFactory.getInstance(getProject()).createModifierFromText(name);
      PsiElement anchor = findAnchor(name);
      addAfter(modifier, anchor);
    }
    else {
      final PsiElement[] modifiers = findChildrenByType(TokenSets.MODIFIERS, PsiElement.class);
      for (PsiElement modifier : modifiers) {
        if (name.equals(modifier.getText())) {
          deleteChildRange(modifier, modifier);
          break;
        }
      }

      if (getTextLength() == 0) {
        final PsiElement nextSibling = getNextSibling();
        if (nextSibling != null && TokenSets.WHITE_SPACES_SET.contains(nextSibling.getNode().getElementType())) {
          nextSibling.delete();
        }
      }
    }
  }

  @Nullable
  private PsiElement findAnchor(String name) {
    final int myPriority = PRIORITY.get(name);
    final PsiElement[] modifiers = getModifiers();
    PsiElement anchor = null;
    for (int i = modifiers.length - 1; i >= 0; i--) {
      PsiElement modifier = modifiers[i];
      if (PRIORITY.get(modifier.getText()) <= myPriority) {
        anchor = modifier;
        break;
      }
    }
    return anchor;
  }

  public void checkSetModifierProperty(@NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
  }

  @NotNull
  public GrAnnotation[] getAnnotations() {
    return getStubOrPsiChildren(GroovyElementTypes.ANNOTATION, ARRAY_FACTORY);
  }

  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Nullable
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    final GrModifierListStub stub = getStub();
    if (stub != null) {
      for (StubElement stubElement : stub.getChildrenStubs()) {
        final PsiElement child = stubElement.getPsi();
        if (child instanceof PsiAnnotation && qualifiedName.equals(((PsiAnnotation)child).getQualifiedName())) {
          return (PsiAnnotation)child;
        }
      }
    } else {
      PsiElement child = getFirstChild();
      while (child != null) {
        if (child instanceof PsiAnnotation && qualifiedName.equals(((PsiAnnotation)child).getQualifiedName())) {
          return (PsiAnnotation)child;
        }
        child = child.getNextSibling();
      }
    }
    return null;
  }

  @NotNull
  public GrAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    final PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(qualifiedName, getResolveScope());
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    GrAnnotation annotation;
    if (psiClass != null && psiClass.isAnnotationType()) {
      annotation = (GrAnnotation)addAfter(factory.createModifierFromText("@xxx"), null);
      annotation.getClassReference().bindToElement(psiClass);
    }
    else {
      annotation = (GrAnnotation)addAfter(factory.createModifierFromText("@" + qualifiedName), null);
    }

    final PsiElement parent = getParent();
    if (!(parent instanceof GrParameter)) {
      final ASTNode node = annotation.getNode();
      final ASTNode treeNext = node.getTreeNext();
      if (treeNext != null) {
        getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", treeNext);
      }
    }

    return annotation;
  }
}
