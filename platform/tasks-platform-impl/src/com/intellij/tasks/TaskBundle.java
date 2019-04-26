package com.intellij.tasks;

import com.intellij.CommonBundle;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.util.ResourceBundle;

/**
 * Contains common and repository specific messages for "Tasks and Contexts" subsystem.
 * Initialization logic follows the same pattern as most of the other bundles in project.
 *
 * @author Mikhail Golubev
 */
public class TaskBundle {

  private static Reference<ResourceBundle> ourBundle;
  @NonNls private static final String BUNDLE = "com.intellij.tasks.TaskBundle";

  private TaskBundle() {
    // empty
  }

  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = SoftReference.dereference(ourBundle);
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      ourBundle = new SoftReference<>(bundle);
    }
    return bundle;
  }
}
