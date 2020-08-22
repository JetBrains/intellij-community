// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class CvsBundle extends DynamicBundle {
  @NonNls public static final String BUNDLE = "messages.CvsBundle";
  private static final CvsBundle INSTANCE = new CvsBundle();

  private CvsBundle() { super(BUNDLE); }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  public static String getCvsDisplayName() {
    return message("general.cvs.display.name");
  }

  public static String getAddingFilesOperationName() {
    return message("operation.name.adding.files");
  }

  public static String getCheckoutOperationName() {
    return message("operation.name.checkout");
  }

  public static String getRollbackOperationName() {
    return message("operation.name.rollback");
  }

  public static String getRollbackButtonText() {
    return message("action.button.text.rollback");
  }

  public static String getViewEditorsOperationName() {
    return message("operation.name.view.editors");
  }

  public static String getAddWatchingOperationName() {
    return message("operation.name.watching.add");
  }

  public static String getMergeOperationName() {
    return message("operation.name.merge");
  }

  public static String getAnnotateOperationName() {
    return message("operation.name.annotate");
  }
}