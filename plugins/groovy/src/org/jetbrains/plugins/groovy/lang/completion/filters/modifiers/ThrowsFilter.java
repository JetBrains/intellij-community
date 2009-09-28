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

package org.jetbrains.plugins.groovy.lang.completion.filters.modifiers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

/**
 * @author ilyas
 */
public class ThrowsFilter implements ElementFilter {

  public boolean isAcceptable(Object element, PsiElement context) {
    PsiElement candidate = null;
    if (GroovyCompletionUtil.isInTypeDefinitionBody(context)) {
      PsiElement run = context;
      while(!(run.getParent() instanceof GrTypeDefinitionBody)) {
        run = run.getParent();
        assert run != null;
      }
      candidate = PsiTreeUtil.getPrevSiblingOfType(run, GrMember.class);
    }
    else if (context.getParent() instanceof PsiErrorElement) {
     candidate = context.getParent().getPrevSibling();
    }

    return candidate instanceof GrMethod && ((GrMethod) candidate).getBlock() == null;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "'throws' keyword filter";
  }

}
