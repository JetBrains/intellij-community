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

package org.jetbrains.plugins.groovy.lang.groovydoc.completion.filters;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocInlinedTag;

/**
 * @author ilyas
 */
public class InlinedTagNameFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    if (context == null) return false;
    ASTNode node = context.getNode();
    return node != null && node.getElementType() == GroovyDocTokenTypes.mGDOC_TAG_NAME &&
        context.getParent() instanceof GrDocInlinedTag;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "Groovydoc tagname filter";
  }
}