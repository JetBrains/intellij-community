// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.IncorrectOperationException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrModifierListStub;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt;

import java.util.*;

@SuppressWarnings("StaticFieldReferencedViaSubclass")
public final class GrModifierListImpl extends GrStubElementBase<GrModifierListStub>
  implements GrModifierList, StubBasedPsiElement<GrModifierListStub>, PsiListLikeElement {

  public static final Object2IntMap<String> NAME_TO_MODIFIER_FLAG_MAP = new Object2IntOpenHashMap<>();
  public static final Map<String, IElementType> NAME_TO_MODIFIER_ELEMENT_TYPE = new HashMap<>();

  private static final Object2IntMap<String> PRIORITY = new Object2IntOpenHashMap<>(16);

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
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.DEFAULT, GrModifierFlags.DEFAULT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.SEALED, GrModifierFlags.SEALED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(GrModifier.NON_SEALED, GrModifierFlags.NON_SEALED_MASK);


    PRIORITY.put(GrModifier.PUBLIC,           0);
    PRIORITY.put(GrModifier.PROTECTED,        0);
    PRIORITY.put(GrModifier.PRIVATE,          0);
    PRIORITY.put(GrModifier.PACKAGE_LOCAL,    0);
    PRIORITY.put(GrModifier.STATIC,           1);
    PRIORITY.put(GrModifier.ABSTRACT,         1);
    PRIORITY.put(GrModifier.DEFAULT,          1);
    PRIORITY.put(GrModifier.FINAL,            2);
    PRIORITY.put(GrModifier.SEALED,           2);
    PRIORITY.put(GrModifier.NON_SEALED,       2);
    PRIORITY.put(GrModifier.NATIVE,           3);
    PRIORITY.put(GrModifier.SYNCHRONIZED,     3);
    PRIORITY.put(GrModifier.STRICTFP,         3);
    PRIORITY.put(GrModifier.TRANSIENT,        3);
    PRIORITY.put(GrModifier.VOLATILE,         3);
    PRIORITY.put(GrModifier.DEF,              4);

    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.PUBLIC, GroovyTokenTypes.kPUBLIC);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.ABSTRACT, GroovyTokenTypes.kABSTRACT);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.DEFAULT, GroovyTokenTypes.kDEFAULT);
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
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.SEALED, GroovyTokenTypes.kSEALED);
    NAME_TO_MODIFIER_ELEMENT_TYPE.put(GrModifier.NON_SEALED, GroovyTokenTypes.kNON_SEALED);
  }

  public GrModifierListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrModifierListImpl(GrModifierListStub stub) {
    this(stub, GroovyElementTypes.MODIFIER_LIST);
  }

  public GrModifierListImpl(GrModifierListStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitModifierList(this);
  }

  @Override
  public String toString() {
    return "Modifiers";
  }

  @Override
  public int getModifierFlags() {
    final GrModifierListStub stub = getGreenStub();
    if (stub != null) {
      return stub.getModifiersFlags();
    }
    else {
      return CachedValuesManager.getCachedValue(this, () -> {
        int flags = 0;
        for (PsiElement modifier : findChildrenByType(TokenSets.MODIFIERS)) {
          flags |= NAME_TO_MODIFIER_FLAG_MAP.getInt(modifier.getText());
        }
        return Result.create(flags, this);
      });
    }
  }

  @Override
  public PsiElement @NotNull [] getModifiers() {
    final ArrayList<PsiElement> result = new ArrayList<>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrAnnotation || TokenSets.MODIFIERS.contains(cur.getNode().getElementType())) {
        result.add(cur);
      }
    }

    return result.toArray(PsiElement.EMPTY_ARRAY);
  }

  @Nullable
  @Override
  public PsiElement getModifier(@GrModifierConstant @NotNull @NonNls String name) {
    return findChildByType(NAME_TO_MODIFIER_ELEMENT_TYPE.get(name));
  }

  @Override
  public boolean hasExplicitVisibilityModifiers() {
    return GrModifierListUtil.hasExplicitVisibilityModifiers(this);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return GrModifierListUtil.hasModifierProperty(this, name);
  }

  @Override
  public boolean hasExplicitModifier(@NotNull String name) {
    return GrModifierListUtil.hasExplicitModifier(this, name);
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
      PsiElement parent = getParent();
      if (getModifiers().length == 0 && !(parent instanceof GrMethod && ((GrMethod)parent).isConstructor())) {
        setModifierProperty(GrModifier.DEF, true);
      }
    }
    else {
      setModifierPropertyInternal(name, doSet);
    }
    if (isEmptyModifierList() && !PsiUtilKt.modifierListMayBeEmpty(this.getParent())) {
      setModifierPropertyInternal(GrModifier.DEF, true);
    }
  }

  @Override
  public GrAnnotation @NotNull [] getRawAnnotations() {
    return getStubOrPsiChildren(GroovyElementTypes.ANNOTATION, GrAnnotation.ARRAY_FACTORY);
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
    final int myPriority = PRIORITY.getInt(name);
    PsiElement anchor = null;

    for (PsiElement modifier : getModifiers()) {
      final int otherPriority = PRIORITY.getInt(modifier.getText());
      if (otherPriority <= myPriority) {
        anchor = modifier;
      }
      else if (anchor != null) {
        break;
      }
    }
    return anchor;
  }

  @Override
  public void checkSetModifierProperty(@NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
  }

  @Override
  public GrAnnotation @NotNull [] getAnnotations() {
    return CachedValuesManager.getCachedValue(this, () -> Result.create(
      GrAnnotationCollector.getResolvedAnnotations(this),
      PsiModificationTracker.MODIFICATION_COUNT, this
    ));
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    //todo[medvedev]
    return getAnnotations();
  }

  @Override
  @Nullable
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    for (GrAnnotation annotation : getAnnotations()) {
      if (annotation.hasQualifiedName(qualifiedName)) {
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
    if (psiClass != null) {
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

  @NotNull
  @Override
  public List<? extends PsiElement> getComponents() {
    return Arrays.asList(getModifiers());
  }
}
