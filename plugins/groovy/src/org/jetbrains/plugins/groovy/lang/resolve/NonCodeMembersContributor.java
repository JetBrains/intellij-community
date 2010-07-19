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
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class NonCodeMembersContributor {
  private static final ExtensionPointName<NonCodeMembersContributor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.membersContributor");

  public abstract void processDynamicElements(@NotNull PsiType qualifierType,
                                              PsiScopeProcessor processor,
                                              PsiElement place,
                                              ResolveState state);

  public static boolean runContributors(@NotNull final PsiType qualifierType,
                                         PsiScopeProcessor processor,
                                         final PsiElement place,
                                         final ResolveState state) {
    final Ref<Boolean> result = Ref.create(true);
    final PsiScopeProcessor wrapper = new DelegatingScopeProcessor(processor) {
      @Override
      public boolean execute(PsiElement element, ResolveState state) {
        if (!result.get()) {
          return false;
        }
        final boolean wantMore = super.execute(element, state);
        result.set(wantMore);
        return wantMore;
      }
    };
    for (final NonCodeMembersContributor contributor : EP_NAME.getExtensions()) {
      contributor.processDynamicElements(qualifierType, wrapper, place, state);
      if (!result.get()) {
        return false;
      }
    }
    return true;
  }

}
