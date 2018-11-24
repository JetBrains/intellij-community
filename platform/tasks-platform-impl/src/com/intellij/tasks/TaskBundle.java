package com.intellij.tasks;

import com.intellij.CommonBundle;
import com.intellij.reference.SoftReference;
import org.apache.commons.httpclient.HttpStatus;
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

  @NotNull
  public static String messageForStatusCode(int statusCode) {
    if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
      return message("failure.login");
    }
    else if (statusCode == HttpStatus.SC_FORBIDDEN) {
      return message("failure.permissions");
    }
    return message("failure.http.error", statusCode, HttpStatus.getStatusText(statusCode));
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
