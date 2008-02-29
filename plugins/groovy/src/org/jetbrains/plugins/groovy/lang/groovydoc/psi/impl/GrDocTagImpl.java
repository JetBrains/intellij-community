/*
 * Copyright 2000-2008 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;

/**
 * @author ilyas
 */
public class GrDocTagImpl extends GroovyDocPsiElementImpl implements GrDocTag {

  public GrDocTagImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDocTag(this);
  }

  public String toString() {
    return "GroovyDocTag";
  }

  public String getName() {
    return getNameIdentifier().getText();
  }

  @NotNull
  public PsiElement getNameIdentifier() {
    PsiElement element = findChildByType(GroovyDocTokenTypes.mGDOC_TAG_NAME);
    assert element != null;
    return element;
  }

}
