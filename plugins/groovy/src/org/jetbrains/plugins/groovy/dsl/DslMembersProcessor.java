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

import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersProcessor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.NameHint;
import com.intellij.util.PairProcessor;
import com.intellij.util.Function;
import com.intellij.openapi.util.text.StringUtil;

import java.util.LinkedHashMap;

/**
 * @author peter
 */
public class DslMembersProcessor implements NonCodeMembersProcessor {
  public boolean processNonCodeMethods(PsiType type, final PsiScopeProcessor processor, final PsiElement place, boolean forCompletion) {
    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass psiClass = classType.resolve();
      if (psiClass != null) {
        final String qname = psiClass.getQualifiedName();
        if (qname != null) {
          return GroovyDslFileIndex.processExecutors(place, new PairProcessor<GroovyFile, GroovyDslExecutor>() {
            public boolean process(GroovyFile groovyFile, GroovyDslExecutor executor) {
              final StringBuilder classText = new StringBuilder();

              executor.processClassVariants(new ClassDescriptor() {
                public String getQualifiedName() {
                  return qname;
                }

                public boolean isInheritor(String qname) {
                  return InheritanceUtil.isInheritor(psiClass, qname);
                }

                public MethodDescriptor[] getMethods() {
                  return new MethodDescriptor[0];
                }
              }, new GroovyEnhancerConsumer() {
                public void property(String name, String type) {
                  classText.append("def ").append(type).append(" ").append(name).append("\n");
                }

                public void method(String name, String type, final LinkedHashMap<String, String> parameters) {
                  classText.append("def ").append(type).append(" ").append(name).append("(");
                  classText.append(StringUtil.join(parameters.keySet(), new Function<String, String>() {
                    public String fun(String s) {
                      return parameters.get(s) + " " + s;
                    }
                  }, ", "));

                  classText.append(") {}\n");
                }

              });

              if (classText.length() > 0) {
                final PsiClass psiClass =
                  GroovyPsiElementFactory.getInstance(place.getProject()).createGroovyFile("class GroovyEnhanced {\n" + classText + "}", false, place)
                    .getClasses()[0];

                final NameHint nameHint = processor.getHint(NameHint.KEY);
                final String expectedName = nameHint == null ? null : nameHint.getName(ResolveState.initial());

                for (PsiMethod method : psiClass.getMethods()) {
                  if ((expectedName == null || expectedName.equals(method.getName())) && !processor.execute(method, ResolveState.initial())) return false;
                }
                for (final PsiField field : psiClass.getFields()) {
                  if ((expectedName == null || expectedName.equals(field.getName())) && !processor.execute(field, ResolveState.initial())) return false;
                }
              }
              return true;
            }
          });

        }
      }
    }
    return true;
  }
}
