package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public interface ErrorReporter {
  void report(@NotNull String message, @NotNull String title);
}
