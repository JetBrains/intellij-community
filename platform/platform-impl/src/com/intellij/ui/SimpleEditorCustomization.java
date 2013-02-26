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
package com.intellij.ui;

/**
 * Editor customization that can be on (enabled) or off (disabled).
 *
 * @author Kirill Likhodedov
 */
public abstract class SimpleEditorCustomization implements EditorCustomization {

  private final boolean myEnabled;

  protected SimpleEditorCustomization(boolean enabled) {
    myEnabled = enabled;
  }

  protected boolean isEnabled() {
    return myEnabled;
  }
}
