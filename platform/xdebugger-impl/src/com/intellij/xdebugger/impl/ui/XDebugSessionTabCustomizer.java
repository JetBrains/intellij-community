package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Experimental
public interface XDebugSessionTabCustomizer {
    @Nullable SessionTabComponentProvider getBottomLocalsComponentProvider();
    interface SessionTabComponentProvider {
        default void visibilityChanged(boolean isVisible) {
        }

        default @Nullable Icon getComponentIcon(){
            return null;
        }

        default @Nullable String getComponentIconPopupText(){
            return null;
        }

        JComponent createBottomLocalsComponent(@NotNull Disposable layoutDisposable);
    }
}
