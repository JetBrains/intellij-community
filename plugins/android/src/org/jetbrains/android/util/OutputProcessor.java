package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public interface OutputProcessor {
  void onTextAvailable(@NotNull String text);
}
