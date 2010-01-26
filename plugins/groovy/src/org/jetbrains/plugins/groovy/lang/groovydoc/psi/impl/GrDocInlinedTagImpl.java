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

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocInlinedTag;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

import java.util.List;

/**
 * @author ilyas
 */
public class GrDocInlinedTagImpl extends GroovyDocPsiElementImpl implements GrDocInlinedTag {

  public GrDocInlinedTagImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDocTag(this);
  }

  public String toString() {
    return "GrDocInlinedTag";
  }

  @NotNull
  public String getName() {
    return getNameElement().getText();
  }

  @NotNull
  public PsiElement getNameElement() {
    PsiElement element = findChildByType(GroovyDocTokenTypes.mGDOC_TAG_NAME);
    assert element != null;
    return element;
  }

  public GrDocComment getContainingComment() {
    return (GrDocComment)getParent();
  }

  public GrDocTagValueToken getValueElement() {
    return findChildByClass(GrDocTagValueToken.class);
  }

  public PsiElement[] getDataElements() {
    final List<PsiElement> list = findChildrenByType(GroovyElementTypes.mGDOC_COMMENT_DATA);
    return list.toArray(new PsiElement[list.size()]);
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final PsiElement nameElement = getNameElement();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    final GrDocComment comment = factory.createDocCommentFromText("/** {@" + name + "}*/");
    nameElement.replace(comment.getTags()[0].getNameElement());
    return this;
  }
}

