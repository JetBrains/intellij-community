// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Holder for NewUi preference that is available from almost all modules
 * can be used instead of [ExperimentalUI.isNewUI].
 * <p>
 * See [ExperimentalUIImpl.setNewUIInternal] for more.
 */
public final class NewUiValue {
  /**
   * @deprecated You probably should not use this key directly,
   *             consider using isEnabled method instead.
   */
  @Deprecated
  public static final String KEY = "ide.experimental.ui";

  private static volatile @Nullable Boolean overrideNewUiForOneRemDevSession = null;
  private static volatile boolean isInitialized = false;
  private static volatile Supplier<Boolean> isEnabled = () -> true;

  public static synchronized void initialize(Supplier<Boolean> enabled) {
    if (isInitialized) {
      return;
    }

    isInitialized = true;
    isEnabled = enabled;
  }

  public static void overrideNewUiForOneRemDevSession(boolean newUi) {
    overrideNewUiForOneRemDevSession = newUi;
  }

  public static boolean isEnabled() {
    Boolean override = overrideNewUiForOneRemDevSession;
    return override == null ? isEnabled.get() : override;
  }
}
