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
package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XValueModifier {

  /**
   * Start modification of the value. Note that this method is called from the Event Dispatch Thread so it should return quickly
   * @param expression new value
   * @param callback used to notify that value has been successfully modified or an error occurs
   */
  public abstract void setValue(@NotNull String expression, @NotNull XModificationCallback callback);

  /**
   * @return return text to show in expression editor when "Set Value" action is invoked
   */
  @Nullable
  public String getInitialValueEditorText() {
    return null;
  }

  public interface XModificationCallback {
    void valueModified();

    void errorOccurred(@NotNull String errorMessage);
  }
}
