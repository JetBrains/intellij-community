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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.*;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

import java.util.*;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public class GrModifierListImpl extends GroovyPsiElementImpl implements GrModifierList {
  public GrModifierListImpl(@NotNull ASTNode node) {
    super(node);
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
    PsiElement defKeyword = findChildByType(GroovyTokenTypes.kDEF);

    if (modifiersKeywords.length != 0)
      modifiers.addAll(Arrays.asList(modifiersKeywords));

    if (modifiersAnnotations.length != 0)
      modifiers.addAll(Arrays.asList(modifiersAnnotations));

    if (defKeyword != null)
      modifiers.add(defKeyword);

    return modifiers.toArray(new PsiElement[modifiers.size()]);
  }

  public boolean hasExplicitVisibilityModifiers() {
    return findChildByType(TokenSets.VISIBILITY_MODIFIERS) != null;
  }

  public boolean hasModifierProperty(@NotNull @NonNls String name) {
    if (name.equals(PsiModifier.PUBLIC)) {
      //groovy type definitions and methods are public by default
      return findChildByType(GroovyElementTypes.kPRIVATE) == null && findChildByType(GroovyElementTypes.kPROTECTED) == null;
    }

    if (name.equals(PsiModifier.PRIVATE)) return findChildByType(GroovyElementTypes.kPRIVATE) != null;
    if (name.equals(PsiModifier.PROTECTED)) return findChildByType(GroovyElementTypes.kPROTECTED) != null;
    if (name.equals(PsiModifier.SYNCHRONIZED)) return findChildByType(GroovyElementTypes.kSYNCHRONIZED) != null;
    if (name.equals(PsiModifier.STRICTFP)) return findChildByType(GroovyElementTypes.kSTRICTFP) != null;
    if (name.equals(PsiModifier.STATIC)) return findChildByType(GroovyElementTypes.kSTATIC) != null;
    if (name.equals(PsiModifier.FINAL)) return findChildByType(GroovyElementTypes.kFINAL) != null;
    if (name.equals(PsiModifier.TRANSIENT)) return findChildByType(GroovyElementTypes.kTRANSIENT) != null;
    if (name.equals(PsiModifier.VOLATILE)) return findChildByType(GroovyElementTypes.kVOLATILE) != null;

    if (!(getParent() instanceof GrVariableDeclaration)) {
      if (name.equals(PsiModifier.ABSTRACT)) return findChildByType(GroovyElementTypes.kABSTRACT) != null;
      if (name.equals(PsiModifier.NATIVE)) return findChildByType(GroovyElementTypes.kNATIVE) != null;
    }

    return false;
  }

  public boolean hasExplicitModifier(@NotNull @NonNls String name) {
    if (name.equals(PsiModifier.PUBLIC)) return findChildByType(GroovyElementTypes.kPUBLIC) != null;
    if (name.equals(PsiModifier.PRIVATE)) return findChildByType(GroovyElementTypes.kPRIVATE) != null;
    if (name.equals(PsiModifier.PROTECTED)) return findChildByType(GroovyElementTypes.kPROTECTED) != null;
    if (name.equals(PsiModifier.STATIC)) return findChildByType(GroovyElementTypes.kSTATIC) != null;
    if (name.equals(PsiModifier.ABSTRACT)) return findChildByType(GroovyElementTypes.kABSTRACT) != null;
    if (name.equals(PsiModifier.FINAL)) return findChildByType(GroovyElementTypes.kFINAL) != null;
    if (name.equals(PsiModifier.NATIVE)) return findChildByType(GroovyElementTypes.kNATIVE) != null;
    if (name.equals(PsiModifier.SYNCHRONIZED)) return findChildByType(GroovyElementTypes.kSYNCHRONIZED) != null;
    if (name.equals(PsiModifier.STRICTFP)) return findChildByType(GroovyElementTypes.kSTRICTFP) != null;
    if (name.equals(PsiModifier.TRANSIENT)) return findChildByType(GroovyElementTypes.kTRANSIENT) != null;
    if (name.equals(PsiModifier.VOLATILE)) return findChildByType(GroovyElementTypes.kVOLATILE) != null;
    return false;
  }

  public void setModifierProperty(@NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
  }

  public void checkSetModifierProperty(@NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return new PsiAnnotation[0];
  }

  @Nullable
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return null;
  }

}
