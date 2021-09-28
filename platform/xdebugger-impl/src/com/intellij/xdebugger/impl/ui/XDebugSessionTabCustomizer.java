package com.intellij.xdebugger.impl.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Experimental
public interface XDebugSessionTabCustomizer {
    @Nullable SessionTabComponentProvider getBottomLocalsComponentProvider();
    interface SessionTabComponentProvider {
        JComponent createBottomLocalsComponent();
    }
}
