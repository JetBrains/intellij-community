package org.jetbrains.android.util;

import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public class ComponentBasedErrorReporter implements ErrorReporter {
  private final JComponent myComponent;

  public ComponentBasedErrorReporter(@NotNull JComponent component) {
    myComponent = component;
  }

  @Override
  public void report(@NotNull String message, @NotNull String title) {
    Messages.showErrorDialog(myComponent, message, title);
  }
}
