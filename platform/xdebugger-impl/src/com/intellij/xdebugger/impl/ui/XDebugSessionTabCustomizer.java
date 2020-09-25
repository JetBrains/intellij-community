package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Experimental
public interface XDebugSessionTabCustomizer {
    JComponent createBottomLocalsComponent(@NotNull Disposable layoutDisposable);

    void visibilityBottomLocalsComponentChange(boolean isVisible);
}
