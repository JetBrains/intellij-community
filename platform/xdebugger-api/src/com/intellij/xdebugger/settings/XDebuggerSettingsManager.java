/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xdebugger.settings;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

public abstract class XDebuggerSettingsManager {
  public static XDebuggerSettingsManager getInstance() {
    return ServiceManager.getService(XDebuggerSettingsManager.class);
  }

  public interface DataViewSettings {
    boolean isSortValues();

    boolean isAutoExpressions();

    int getValueLookupDelay();

    boolean isShowLibraryStackFrames();

    boolean isShowValuesInline();
  }

  @NotNull
  public abstract DataViewSettings getDataViewSettings();
}