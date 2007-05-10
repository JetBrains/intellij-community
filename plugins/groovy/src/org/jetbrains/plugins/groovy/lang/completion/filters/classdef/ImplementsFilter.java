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

package org.jetbrains.plugins.groovy.lang.completion.filters.classdef;

import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiComment;
import com.intellij.psi.impl.PsiElementBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 */
public class ImplementsFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    if (context.getParent() != null &&
        !(context.getParent() instanceof GrImplementsClause)) {
      PsiElement elem = context.getParent().getPrevSibling();
      while (elem != null &&
          (elem instanceof PsiWhiteSpace ||
              elem instanceof PsiComment ||
              GroovyElementTypes.mNLS.equals(elem.getNode().getElementType()))) {
        elem = elem.getPrevSibling();
      }

      if (elem instanceof GrEnumTypeDefinition ||
          (elem instanceof GrClassDefinition)) {
        PsiElement[] children = elem.getChildren();
        for (PsiElement child : children) {
          if (child instanceof GrImplementsClause ||
              child instanceof GrTypeDefinitionBody) {
            return false;
          }
        }
        return true;
      }
      return false;
    }
    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "Control structure keywords filter";
  }
}
