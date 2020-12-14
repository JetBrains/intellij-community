/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Gradle expression, this can be any simple literal, list, map, reference or method call
 * For example, all of the following will be GradleDslExpressions:
 *   prop = "hello"
 *   targetSdkVersion 24
 *   useMagic true
 *   google()
 *   proguardFiles 'file1.txt', 'file2.txt'
 *   compile group: 'group', name: 'hello', version: 1.0
 */
public interface GradleDslExpression extends GradleDslElement {
  @Nullable
  PsiElement getExpression();

  @NotNull
  GradleDslExpression copy();
}
