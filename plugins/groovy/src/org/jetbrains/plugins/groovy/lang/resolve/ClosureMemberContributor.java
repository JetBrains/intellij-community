/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

/**
 * @author Sergey Evdokimov
 */
public abstract class ClosureMemberContributor extends NonCodeMembersContributor {
  @Override
  public final void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     GroovyPsiElement place,
                                     ResolveState state) {
    if (!(qualifierType instanceof GrClosureType)) return;

    GrClosableBlock closure = (GrClosableBlock)state.get(ResolverProcessor.RESOLVE_CONTEXT);
    if (closure == null) return;

    processMembers(closure, processor, place, state);
  }

  protected abstract void processMembers(@NotNull GrClosableBlock closure,
                                         PsiScopeProcessor processor,
                                         GroovyPsiElement place,
                                         ResolveState state);
}
