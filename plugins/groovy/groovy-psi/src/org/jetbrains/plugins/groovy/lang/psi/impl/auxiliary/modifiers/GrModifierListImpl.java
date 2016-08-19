/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ArrayFactory;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrModifierListStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Map;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
@SuppressWarnings({"StaticFieldReferencedViaSubclass"})
public class GrModifierListImpl extends GrStubElementBase<GrModifierListStub> implements GrModifierList, StubBasedPsiElement<GrModifierListStub> {
  public static final TObjectIntHashMap<String> NAME_TO_MODIFIER_FLAG_MAP = new TObjectIntHashMap<>();
  public static final Map<String, IElementType> NAME_TO_MODIFIER_ELEMENT_TYPE = ContainerUtil.newHashMap();
  private static final ArrayFactory<GrAnnotation> ARRAY_FACTORY = GrAnnotation[]::new;

  private static final TObjectIntHashMap<String> PRIORITY = new TObjectIntHashMap<>(16);

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

    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.PUBLIC, GroovyTokenTypes.kPUBLIC);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.ABSTRACT, GroovyTokenTypes.kABSTRACT);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.NATIVE, GroovyTokenTypes.kNATIVE);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.PRIVATE, GroovyTokenTypes.kPRIVATE);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.PROTECTED, GroovyTokenTypes.kPROTECTED);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.SYNCHRONIZED, GroovyTokenTypes.kSYNCHRONIZED);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.STRICTFP, GroovyTokenTypes.kSTRICTFP);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.STATIC, GroovyTokenTypes.kSTATIC);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.FINAL, GroovyTokenTypes.kFINAL);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.TRANSIENT, GroovyTokenTypes.kTRANSIENT);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.NATIVE, GroovyTokenTypes.kNATIVE);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.DEF, GroovyTokenTypes.kDEF);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.VOLATILE, GroovyTokenTypes.kVOLATILE);
  }

  private static final String[] VISIBILITY_MODIFIERS = {GrModifier.PUBLIC, GrModifier.PROTECTED, GrModifier.PRIVATE};

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

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitModifierList(this);
  }

  public String toString() {
    return "Modifiers";
  }

  @Override
  @NotNull
  public PsiElement[] getModifiers() {
    final ArrayList<PsiElement> result = new ArrayList<>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrAnnotation || TokenSets.MODIFIERS.contains(cur.getNode().getElementType())) {
        result.add(cur);
      }
    }

    return result.toArray(new PsiElement[result.size()]);
  }

  @Nullable
  @Override
  public PsiElement getModifier(@GrModifier.GrModifierConstant @NotNull @NonNls String name) {
    return findChildByType(NAME_TO_MODIFIER_ELEMENT_TYPE.get(name));
  }

  @Override
  public boolean hasExplicitVisibilityModifiers() {
    final GrModifierListStub stub = getStub();
    if (stub != null) {
      return (stub.getModifiersFlags() & (GrModifierFlags.PUBLIC_MASK | GrModifierFlags.PROTECTED_MASK | GrModifierFlags.PRIVATE_MASK)) != 0;
    }

    for (@GrModifier.GrModifierConstant String type : VISIBILITY_MODIFIERS) {
      if (hasExplicitModifier(type)) return true;
    }
    return false;
  }

  public static boolean checkModifierProperty(@NotNull GrModifierList modifierList, @GrModifier.GrModifierConstant @NotNull String modifier) {
    final PsiElement owner = modifierList.getParent();
    if (owner instanceof GrVariableDeclaration && owner.getParent() instanceof GrTypeDefinitionBody) {
      PsiElement pParent = owner.getParent().getParent();
      if (!modifierList.hasExplicitVisibilityModifiers()) { //properties are backed by private fields
        if (!(pParent instanceof GrTypeDefinition && GrTraitUtil.isInterface((GrTypeDefinition)pParent))) {
          if (modifier.equals(GrModifier.PRIVATE)) return true;
          if (modifier.equals(GrModifier.PROTECTED)) return false;
          if (modifier.equals(GrModifier.PUBLIC)) return false;
        }
      }

      if (pParent instanceof GrTypeDefinition && GrTraitUtil.isInterface((GrTypeDefinition)pParent)) {
        if (modifier.equals(GrModifier.STATIC)) return true;
        if (modifier.equals(GrModifier.FINAL)) return true;
      }
      if (pParent instanceof GrTypeDefinition && modifier.equals(GrModifier.FINAL) && !modifierList.hasExplicitVisibilityModifiers()) {
        PsiModifierList pModifierList = ((GrTypeDefinition)pParent).getModifierList();
        if (pModifierList != null && PsiImplUtil.hasImmutableAnnotation(pModifierList)) {
          return true;
        }
      }
    }

    if (owner instanceof GrMethod && owner.getParent() instanceof GrTypeDefinitionBody) {
      PsiElement parent = owner.getParent().getParent();
      if (parent instanceof GrTypeDefinition && ((GrTypeDefinition)parent).isInterface()) {
        if (GrModifier.ABSTRACT.equals(modifier)) return true;
        if (!((GrTypeDefinition)parent).isTrait() && GrModifier.PUBLIC.equals(modifier)) return true;
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
      final GrTypeDefinition clazz = (GrTypeDefinition)owner;

      if (modifier.equals(GrModifier.STATIC)) {
        final PsiClass containingClass = clazz.getContainingClass();
        return GrTraitUtil.isInterface(containingClass);
      }
      if (modifier.equals(GrModifier.ABSTRACT)) {
        if (clazz.isInterface()) return true;
        if (clazz.isEnum() &&
            GroovyConfigUtils.getInstance().isVersionAtLeast(modifierList, GroovyConfigUtils.GROOVY2_0)) {
          for (GrMethod method : clazz.getCodeMethods()) {
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
          }
        }
      }
      if (modifier.equals(GrModifier.FINAL)) {
        if (clazz.isEnum()) {
          final GrField[] fields = clazz.getFields();
          for (GrField field : fields) {
            if (field instanceof GrEnumConstant && ((GrEnumConstant)field).getInitializingClass() != null) {
              return false;
            }
          }
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public boolean hasModifierProperty(@NotNull @NonNls String modifier) {
    return checkModifierProperty(this, modifier);
  }

  @Override
  public boolean hasExplicitModifier(@NotNull @NonNls String name) {
    final GrModifierListStub stub = getStub();
    if (stub != null) {
      return hasMaskExplicitModifier(name, stub.getModifiersFlags());
    }

    return getModifier(name) != null;
  }

  public static boolean hasMaskExplicitModifier(String name, int mask) {
    final int flag = NAME_TO_MODIFIER_FLAG_MAP.get(name);
    return (mask & flag) != 0;
  }

  @Override
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
    if (isEmptyModifierList() && !PsiUtil.modifierListMayBeEmpty(this.getParent())) {
      setModifierPropertyInternal(GrModifier.DEF, true);
    }
  }

  @NotNull
  @Override
  public GrAnnotation[] getRawAnnotations() {
    return getStubOrPsiChildren(GroovyElementTypes.ANNOTATION, ARRAY_FACTORY);
  }

  private void setModifierPropertyInternal(String name, boolean doSet) {
    if (doSet) {
      if (isEmptyModifierList()) {
        final PsiElement nextSibling = getNextSibling();
        if (nextSibling != null && !PsiImplUtil.isWhiteSpaceOrNls(nextSibling)) {
          getNode().getTreeParent().addLeaf(TokenType.WHITE_SPACE, " ", nextSibling.getNode());
        }
      }

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

      if (isEmptyModifierList()) {
        final PsiElement nextSibling = getNextSibling();
        if (nextSibling != null && PsiImplUtil.isWhiteSpaceOrNls(nextSibling)) {
          nextSibling.delete();
        }
      }
    }
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    final ASTNode node = super.addInternal(first, last, anchor, before);
    final PsiElement sibling = getNextSibling();
    if (sibling != null && sibling.getText().contains("\n")) {
      sibling.replace(GroovyPsiElementFactory.getInstance(getProject()).createWhiteSpace());
    }
    return node;
  }

  private boolean isEmptyModifierList() {
    return getTextLength() == 0 || getModifiers().length == 0 && getRawAnnotations().length == 0;
  }

  @Nullable
  private PsiElement findAnchor(String name) {
    final int myPriority = PRIORITY.get(name);
    PsiElement anchor = null;

    for (PsiElement modifier : getModifiers()) {
      final int otherPriority = PRIORITY.get(modifier.getText());
      if (otherPriority <= myPriority) {
        anchor = modifier;
      }
      else if (otherPriority > myPriority && anchor != null) {
        break;
      }
    }
    return anchor;
  }

  @Override
  public void checkSetModifierProperty(@NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
  }

  @Override
  @NotNull
  public GrAnnotation[] getAnnotations() {
    return CachedValuesManager.getCachedValue(this,
                                              () -> CachedValueProvider.Result
                                                .create(GrAnnotationCollector.getResolvedAnnotations(this), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
  }

  @Override
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    //todo[medvedev]
    return getAnnotations();
  }

  @Override
  @Nullable
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    for (GrAnnotation annotation : getAnnotations()) {
      if (qualifiedName.equals(annotation.getQualifiedName())) {
        return annotation;
      }
    }
    return null;
  }

  @Override
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
        getNode().addLeaf(TokenType.WHITE_SPACE, "\n", treeNext);
      }
      else {
        parent.getNode().addLeaf(TokenType.WHITE_SPACE, "\n", getNode().getTreeNext());
      }
    }

    return annotation;
  }
}
