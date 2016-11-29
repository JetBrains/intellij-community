/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 04.04.2007
 */
public class GrAnnotationArrayInitializerImpl extends GroovyPsiElementImpl implements GrAnnotationArrayInitializer {
  public GrAnnotationArrayInitializerImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotationArrayInitializer(this);
  }

  public String toString() {
    return "Annotation array initializer";
  }

  @Override
  @NotNull
  public GrAnnotationMemberValue[] getInitializers() {
    List<GrAnnotationMemberValue> result = new ArrayList<>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrAnnotationMemberValue) result.add((GrAnnotationMemberValue)cur);
    }
    return result.toArray(new GrAnnotationMemberValue[result.size()]);
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    final GrAnnotationMemberValue[] initializers = getInitializers();
    if (initializers.length == 0) {
      return super.addInternal(first, last, getNode().getFirstChildNode(), false);
    }
    final ASTNode lastChild = getNode().getLastChildNode();
    getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", lastChild);
    return super.addInternal(first, last, lastChild.getTreePrev(), false);
  }
}
