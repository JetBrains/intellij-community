package com.intellij.tasks;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class TaskBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "com.intellij.tasks.TaskBundle";
  private static final TaskBundle INSTANCE = new TaskBundle();

  private TaskBundle() { super(BUNDLE); }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}