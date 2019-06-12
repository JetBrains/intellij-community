// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * @author yole
 */
@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
public class CommonBundle extends BundleBase {
  private static final String BUNDLE = "messages.CommonBundle";
  private static Reference<ResourceBundle> ourBundle;

  private CommonBundle() { }

  @Nls
  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return message(getCommonBundle(), key, params);
  }

  @NotNull
  private static ResourceBundle getCommonBundle() {
    ResourceBundle bundle = com.intellij.reference.SoftReference.dereference(ourBundle);
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      ourBundle = new SoftReference<>(bundle);
    }
    return bundle;
  }

  public static String messageOrDefault(@Nullable ResourceBundle bundle, @NotNull String key, @Nullable String defaultValue, @NotNull Object... params) {
    if (bundle == null) return defaultValue;
    if (!bundle.containsKey(key)) {
      return postprocessValue(bundle, useDefaultValue(bundle, key, defaultValue), params);
    }
    return BundleBase.messageOrDefault(bundle, key, defaultValue, params);
  }

  @Nls
  @NotNull
  public static String message(@NotNull ResourceBundle bundle, @NotNull String key, @NotNull Object... params) {
    return BundleBase.message(bundle, key, params);
  }

  @Nullable
  public static String messageOfNull(@NotNull ResourceBundle bundle, @NotNull String key, @NotNull Object... params) {
    String value = messageOrDefault(bundle, key, key, params);
    if (key.equals(value)) return null;
    return value;
  }

  @NotNull
  public static String getCancelButtonText() {
    return message("button.cancel");
  }

  public static String getBackgroundButtonText() {
    return message("button.background");
  }

  public static String getHelpButtonText() {
    return message("button.help");
  }

  public static String getErrorTitle() {
    return message("title.error");
  }

  /**
   * @deprecated Use more informative title instead
   */
  @Deprecated
  public static String getWarningTitle() {
    return message("title.warning");
  }

  public static String getLoadingTreeNodeText() {
    return message("tree.node.loading");
  }

  public static String getOkButtonText() {
    return message("button.ok");
  }

  public static String getYesButtonText() {
    return message("button.yes");
  }

  public static String getNoButtonText() {
    return message("button.no");
  }

  public static String getContinueButtonText() {
    return message("button.continue");
  }

  public static String getYesForAllButtonText() {
    return message("button.yes.for.all");
  }

  public static String getCloseButtonText() {
    return message("button.close");
  }

  public static String getNoForAllButtonText() {
    return message("button.no.for.all");
  }

  public static String getApplyButtonText() {
    return message("button.apply");
  }

  public static String getAddButtonText() {
    return message("button.add.a");
  }

  public static String settingsTitle() {
    return SystemInfo.isMac ? message("title.settings.mac") : message("title.settings");
  }

  public static String settingsAction() {
    return SystemInfo.isMac ? message("action.settings.mac") : message("action.settings");
  }

  public static String settingsActionDescription() {
    return SystemInfo.isMac ? message("action.settings.description.mac") : message("action.settings.description");
  }

  public static String settingsActionPath() {
    return SystemInfo.isMac ? message("action.settings.path.mac") : message("action.settings.path");
  }
}