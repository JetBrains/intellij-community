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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifiers;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public class GrModifiersImpl extends GroovyPsiElementImpl implements GrModifiers {
  public GrModifiersImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Modifiers";
  }

  @NotNull
  public GroovyPsiElement[] getModifierList() {
    List<PsiElement> modifiers = new ArrayList<PsiElement>();
    PsiElement[] modifiersKeywords = findChildrenByType(TokenSets.MODIFIERS, GroovyPsiElement.class);
    GrAnnotation[] modifiersAnnotations = findChildrenByClass(GrAnnotation.class);
    PsiElement defKeyword = findChildByType(GroovyTokenTypes.kDEF);

    if (modifiersKeywords.length != 0)
      modifiers.addAll(Arrays.asList(modifiersKeywords));

    if (modifiersAnnotations.length != 0)
      modifiers.addAll(Arrays.asList(modifiersAnnotations));

    if (defKeyword != null)
      modifiers.add(defKeyword);

    return modifiers.toArray(new GroovyPsiElement[0]);
  }
}
