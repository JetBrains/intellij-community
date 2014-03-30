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
package com.intellij.xdebugger.frame.presentation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a value using default color. If you only need to show {@code value} and {@code type}
 * use {@link com.intellij.xdebugger.frame.XValueNode#setPresentation(javax.swing.Icon, String, String, boolean) setPresentation} method instead
 *
 * @author nik
*/
public class XRegularValuePresentation extends XValuePresentation {
  private final String myType;
  private final String myValue;
  private final String mySeparator;

  public XRegularValuePresentation(@NotNull String value, @Nullable String type) {
    this(value, type, DEFAULT_SEPARATOR);
  }

  public XRegularValuePresentation(@NotNull String value, @Nullable String type, final @NotNull String separator) {
    myValue = value;
    myType = type;
    mySeparator = separator;
  }

  public String getType() {
    return myType;
  }

  @NotNull
  @Override
  public String getSeparator() {
    return mySeparator;
  }

  @Override
  public void renderValue(@NotNull XValueTextRenderer renderer) {
    renderer.renderValue(myValue);
  }
}
