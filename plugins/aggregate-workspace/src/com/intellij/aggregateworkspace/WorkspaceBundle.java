package com.intellij.aggregateworkspace;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.PropertyKey;

public final class WorkspaceBundle {
  private static final String BUNDLE = "messages.WorkspaceBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(BUNDLE);

  private WorkspaceBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}
