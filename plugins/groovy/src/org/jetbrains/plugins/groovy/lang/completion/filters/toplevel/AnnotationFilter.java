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

package org.jetbrains.plugins.groovy.lang.completion.filters.toplevel;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

/**
 * @author ilyas
 */
public class AnnotationFilter implements ElementFilter, GroovyElementTypes {


  public boolean isAcceptable(Object element, PsiElement context) {
    PsiElement previous = GroovyCompletionUtil.realPrevious(context.getParent().getPrevSibling());
    if (previous == null ||
        !GroovyTokenTypes.mAT.equals(previous.getNode().getElementType())) {
      return false;
    }
    if (context.getParent() instanceof GrReferenceElement &&
        context.getParent().getParent() instanceof GrAnnotation) {
      return true;
    }
    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "@interface keyword filter";
  }
}
