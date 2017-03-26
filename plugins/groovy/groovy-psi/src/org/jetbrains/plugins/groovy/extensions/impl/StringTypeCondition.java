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
package org.jetbrains.plugins.groovy.extensions.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

public class StringTypeCondition extends NamedArgumentDescriptorImpl {

  private final @NotNull String myTypeName;

  public StringTypeCondition(@NotNull String typeName) {
    this(typeName, null);
  }

  public StringTypeCondition(@NotNull String typeName, @Nullable PsiElement navigationElement) {
    super(navigationElement);
    myTypeName = typeName;
  }

  public StringTypeCondition(@NotNull Priority priority, @NotNull String typeName) {
    super(priority);
    myTypeName = typeName;
  }

  @Override
  public boolean checkType(@NotNull PsiType type, @NotNull GroovyPsiElement context) {
    return InheritanceUtil.isInheritor(type, myTypeName);
  }
}
