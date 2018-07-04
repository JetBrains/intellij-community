// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
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
    GroovyDslFileIndex.processExecutors(qtype, ref, new PsiScopeProcessor() {
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
