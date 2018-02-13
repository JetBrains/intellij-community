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
package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ImmediateFullValueEvaluator extends XFullValueEvaluator {
  private final String myFullValue;

  public ImmediateFullValueEvaluator(@NotNull String fullValue) {
    myFullValue = fullValue;
  }

  public ImmediateFullValueEvaluator(@NotNull String linkText, @NotNull String fullValue) {
    super(linkText);
    myFullValue = fullValue;
  }

  @Override
  public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
    callback.evaluated(myFullValue);
  }
}
