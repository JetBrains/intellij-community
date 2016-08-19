/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.completion.closureParameters.ClosureDescriptor;
import org.jetbrains.plugins.groovy.lang.completion.closureParameters.ClosureParameterInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

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
                                                         PsiElement place) {
    final ArrayList<ClosureDescriptor> descriptors = new ArrayList<>();
    GrReferenceExpression ref = (GrReferenceExpression)place;
    PsiType qtype = PsiImplUtil.getQualifierType(ref);
    if (qtype == null) return null;

    GrExpression qualifier = ref.getQualifier();
    if (qualifier != null) {
      PsiType type = qualifier.getType();
      if (type == null) return null;
      processExecutors(qtype, ref, descriptors);
    }
    else {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
      for (PsiElement parent = ref.getParent(); parent != null; parent = parent.getParent()) {
        if (parent instanceof GrClosableBlock) {
          processExecutors(TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, ref), ref, descriptors);
        }
        else if (parent instanceof GrTypeDefinition) {
          processExecutors(factory.createType(((GrTypeDefinition)parent), PsiType.EMPTY_ARRAY), ref, descriptors);
        }
      }
    }

    for (ClosureDescriptor descriptor : descriptors) {
      if (descriptor.isMethodApplicable(method, ref)) {
        return descriptor.getParameters();
      }
    }
    return null;
  }

  private static void processExecutors(PsiType qtype, GrReferenceExpression ref, final ArrayList<ClosureDescriptor> descriptors) {
    GroovyDslFileIndex.processExecutors(qtype, ref, new BaseScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element instanceof ClosureDescriptor) {
          descriptors.add((ClosureDescriptor)element);
        }
        return true;
      }
    }, ResolveState.initial());
  }
}
