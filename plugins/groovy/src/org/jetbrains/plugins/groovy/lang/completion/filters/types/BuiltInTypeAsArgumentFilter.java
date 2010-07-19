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

package org.jetbrains.plugins.groovy.lang.completion.filters.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * @author ilyas
 */
public class BuiltInTypeAsArgumentFilter implements ElementFilter {


  public boolean isAcceptable(Object element, PsiElement context) {
    final PsiElement parent = context.getParent();
    if (parent == null) {
      throw new AssertionError("No parent for " + context);
    }

    PsiElement previous = PsiImplUtil.realPrevious(parent.getPrevSibling());
    if (parent instanceof GrReferenceElement && parent.getParent() instanceof GrArgumentList) {
      PsiElement prevSibling = context.getPrevSibling();
      if (parent instanceof GrReferenceElement && prevSibling != null && prevSibling.getNode() != null) {
        ASTNode node = prevSibling.getNode();
        return !GroovyTokenTypes.DOTS.contains(node.getElementType());
      } else {
        return !(previous != null && GroovyTokenTypes.mAT.equals(previous.getNode().getElementType()));
      }

    }
    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "built-in-types as arguments filter";
  }

}
