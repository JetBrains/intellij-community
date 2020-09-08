// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class MLCompletionBundle extends DynamicBundle {
  private static final String ML_COMPLETION_BUNDLE = "messages.MlCompletionBundle";

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = ML_COMPLETION_BUNDLE) String key, Object @NotNull ... params) {
    return ourInstance.getMessage(key, params);
  }

  private static final AbstractBundle ourInstance = new MLCompletionBundle();

  protected MLCompletionBundle() {
    super(ML_COMPLETION_BUNDLE);
  }
}
