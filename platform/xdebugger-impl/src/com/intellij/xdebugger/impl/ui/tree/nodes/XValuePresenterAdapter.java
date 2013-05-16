/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.NotNullFunction;
import com.intellij.xdebugger.frame.XValuePresenter;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;

public class XValuePresenterAdapter implements XValuePresenter {
  private final NotNullFunction<String, String> valuePresenter;

  public XValuePresenterAdapter(NotNullFunction<String, String> valuePresenter) {
    this.valuePresenter = valuePresenter;
  }

  @Override
  public void append(String value, SimpleColoredText text, boolean changed) {
    text.append(valuePresenter.fun(value), changed ? XDebuggerUIConstants.CHANGED_VALUE_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }
}