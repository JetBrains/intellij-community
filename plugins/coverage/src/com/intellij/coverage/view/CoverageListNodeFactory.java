/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.coverage.view;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

/**
 * An interface used to expose {@link CoverageListNode} creation to implementations of
 * {@link com.intellij.coverage.JavaCoverageEngineExtension}.
 *
 * @author Roman.Shein
 * @since 24.02.2015.
 *
 */
public interface CoverageListNodeFactory {

  /**
   * Creates a new node for coverage results report containing parameter as its value.
   * @param namedElement psi element to create node from
   * @return the created node
   */
  @NotNull
  public CoverageListNode createListNode(@NotNull PsiNamedElement namedElement);
}
