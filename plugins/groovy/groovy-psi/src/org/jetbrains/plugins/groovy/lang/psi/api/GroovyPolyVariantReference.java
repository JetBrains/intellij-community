/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.PsiPolyVariantReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * Same as {@link PsiPolyVariantReference} but returns {@link GroovyResolveResult}.
 */
public interface GroovyPolyVariantReference extends PsiPolyVariantReference {

  GroovyPolyVariantReference[] EMPTY_ARRAY = new GroovyPolyVariantReference[0];

  @NotNull
  @Override
  GroovyResolveResult[] multiResolve(boolean incompleteCode);

  @NotNull
  default GroovyResolveResult advancedResolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve(false));
  }
}
