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

package org.jetbrains.plugins.groovy.lang.completion.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.util.ArrayUtil;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

/**
 * @author ven
 */
public class ClassesGetter implements ContextGetter {

  protected static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.completion.getters.ClassesGetter");

  public Object[] get(PsiElement context, CompletionContext completionContext) {
    if (context != null) {
      ResolverProcessor processor = CompletionProcessor.createClassCompletionProcessor(context);
      ResolveUtil.treeWalkUp(context, processor, false);
      return GroovyCompletionUtil.getCompletionVariants(processor.getCandidates());
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
