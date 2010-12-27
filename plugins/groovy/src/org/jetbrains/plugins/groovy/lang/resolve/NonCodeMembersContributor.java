/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author peter
 */
public abstract class NonCodeMembersContributor {
  private static final ExtensionPointName<NonCodeMembersContributor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.membersContributor");

  public abstract void processDynamicElements(@NotNull PsiType qualifierType,
                                              PsiScopeProcessor processor,
                                              GroovyPsiElement place,
                                              ResolveState state);

  public static boolean runContributors(@NotNull final PsiType qualifierType,
                                         PsiScopeProcessor processor,
                                         final GroovyPsiElement place,
                                         final ResolveState state) {

    MyDelegatingScopeProcessor delegatingProcessor = new MyDelegatingScopeProcessor(processor);

    for (final NonCodeMembersContributor contributor : EP_NAME.getExtensions()) {
      contributor.processDynamicElements(qualifierType, delegatingProcessor, place, state);
      if (!delegatingProcessor.wantMore) {
        return false;
      }
    }
    return true;
  }

  private static class MyDelegatingScopeProcessor extends DelegatingScopeProcessor {
    public boolean wantMore = true;

    public MyDelegatingScopeProcessor(PsiScopeProcessor delegate) {
      super(delegate);
    }

    @Override
    public boolean execute(PsiElement element, ResolveState state) {
      if (!wantMore) {
        return false;
      }
      wantMore = super.execute(element, state);
      return wantMore;
    }
  }

}
