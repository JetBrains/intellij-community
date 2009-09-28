/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
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
 */

package org.jetbrains.plugins.groovy.dsl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersProcessor;

/**
 * @author peter
 */
public class DslMembersProcessor implements NonCodeMembersProcessor {
  public boolean processNonCodeMembers(PsiType type, final PsiScopeProcessor processor, final PsiElement place, boolean forCompletion) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null && !(psiClass instanceof GroovyScriptClass)) {
        final String qname = psiClass.getQualifiedName();
        if (qname != null) {
          return GroovyDslFileIndex.processExecutors(psiClass.getProject(), new GroovyClassDescriptor(psiClass, place), processor);
        }
      }
    }
    return true;
  }

}
