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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public interface GroovyResolveResult extends ResolveResult {

  @Deprecated
  GroovyResolveResult EMPTY_RESULT = EmptyGroovyResolveResult.INSTANCE;

  GroovyResolveResult[] EMPTY_ARRAY = new GroovyResolveResult[0];

  boolean isAccessible();

  boolean isStaticsOK();

  boolean isApplicable();

  @Nullable
  PsiElement getCurrentFileResolveContext();

  @NotNull
  PsiSubstitutor getSubstitutor();

  boolean isInvokedOnProperty();

  @Nullable
  SpreadState getSpreadState();
}
