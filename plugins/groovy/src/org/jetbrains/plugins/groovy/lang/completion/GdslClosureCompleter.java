/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.completion.closureParameters.ClosureDescriptor;
import org.jetbrains.plugins.groovy.lang.completion.closureParameters.ClosureParameterInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GdslClosureCompleter extends ClosureCompleter {
  @Override
  protected List<ClosureParameterInfo> getParameterInfos(InsertionContext context,
                                                         PsiMethod method,
                                                         PsiSubstitutor substitutor,
                                                         Document document,
                                                         int offset,
                                                         PsiElement parent) {

    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;

    PsiType type = JavaPsiFacade.getElementFactory(context.getProject()).createType(aClass);

    final ArrayList<ClosureDescriptor> descriptors = new ArrayList<ClosureDescriptor>();
    GroovyDslFileIndex.processExecutors(type, ((GrReferenceExpression)parent), new BaseScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, ResolveState state) {
        if (element instanceof ClosureDescriptor) {
          descriptors.add((ClosureDescriptor)element);
        }
        return true;
      }
    }, ResolveState.initial());

    for (ClosureDescriptor descriptor : descriptors) {
      if (descriptor.isMethodApplicable(method, (GrReferenceExpression)parent)) {
        return descriptor.getParameters();
      }
    }
    return null;
  }
}
