// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class XDebuggerSettingsManager implements Disposable {
  public static XDebuggerSettingsManager getInstance() {
    return ApplicationManager.getApplication().getService(XDebuggerSettingsManager.class);
  }

  public interface DataViewSettings {
    @ApiStatus.Internal
    int DEFAULT_VALUE_TOOLTIP_DELAY = 700;

    boolean isSortValues();

    @ApiStatus.Internal
    void setSortValues(boolean sortValues);

    boolean isAutoExpressions();

    @ApiStatus.Internal
    void setAutoExpressions(boolean autoExpressions);

    int getValueLookupDelay();

    @ApiStatus.Internal
    void setValueLookupDelay(int valueLookupDelay);

    boolean isShowLibraryStackFrames();

    @ApiStatus.Internal
    void setShowLibraryStackFrames(boolean showLibraryStackFrames);

    boolean isShowValuesInline();

    @ApiStatus.Internal
    void setShowValuesInline(boolean showValuesInline);
  }

  @ApiStatus.Internal
  public interface GeneralSettings {
    boolean isShowDebuggerOnBreakpoint();
    void setShowDebuggerOnBreakpoint(boolean value);

    boolean isHideDebuggerOnProcessTermination();
    void setHideDebuggerOnProcessTermination(boolean value);

    boolean isScrollToCenter();
    void setScrollToCenter(boolean value);

    boolean isConfirmBreakpointRemoval();
    void setConfirmBreakpointRemoval(boolean confirmBreakpointRemoval);

    boolean isRunToCursorGestureEnabled();
    void setRunToCursorGestureEnabled(boolean value);

    @NotNull
    EvaluationMode getEvaluationDialogMode();
    void setEvaluationDialogMode(@NotNull EvaluationMode mode);

    boolean isUnmuteOnStop();
    void setUnmuteOnStop(boolean value);
  }

  public abstract @NotNull DataViewSettings getDataViewSettings();

  @ApiStatus.Internal
  public abstract @NotNull GeneralSettings getGeneralSettings();

  @ApiStatus.Internal
  public abstract void forEachSettings(@NotNull Consumer<XDebuggerSettings> consumer);

  @ApiStatus.Internal
  public abstract @Nullable XDebuggerSettings<?> findFirstSettings(@NotNull Predicate<XDebuggerSettings> predicate);
}