// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public abstract class XDebuggerSettingsManager implements Disposable {
  public static XDebuggerSettingsManager getInstance() {
    return ApplicationManager.getApplication().getService(XDebuggerSettingsManager.class);
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