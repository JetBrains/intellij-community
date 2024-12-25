// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public abstract @NotNull DataViewSettings getDataViewSettings();
}