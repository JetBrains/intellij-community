// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.*;

import java.util.ResourceBundle;

import static com.intellij.BundleUtil.loadLanguageBundle;

/**
 * @author yole
 */
@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
public final class CommonBundle extends BundleBase {
  private static final String BUNDLE = "messages.CommonBundle";
  private static ResourceBundle ourBundle;

  private CommonBundle() { }

  @Nls
  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    ResourceBundle bundle = getCommonBundle();
    if (!bundle.containsKey(key)) {
      return UtilBundle.message(key, params);
    }
    return message(bundle, key, params);
  }

  @NotNull
  private static ResourceBundle getCommonBundle() {
    if (ourBundle != null) return ourBundle;
    return ourBundle = ResourceBundle.getBundle(BUNDLE);
  }

  @Contract("null, _, _, _ -> param3")
  public static String messageOrDefault(@Nullable ResourceBundle bundle,
                                        @NotNull String key,
                                        @Nullable String defaultValue,
                                        Object @NotNull ... params) {
    if (bundle == null) {
      return defaultValue;
    }
    else if (!bundle.containsKey(key)) {
      return postprocessValue(bundle, useDefaultValue(bundle, key, defaultValue), params);
    }
    return BundleBase.messageOrDefault(bundle, key, defaultValue, params);
  }

  @Nls
  @NotNull
  public static String message(@NotNull ResourceBundle bundle, @NotNull String key, Object @NotNull ... params) {
    return BundleBase.message(bundle, key, params);
  }

  @Nullable
  public static String messageOfNull(@NotNull ResourceBundle bundle, @NotNull String key, Object @NotNull ... params) {
    String value = messageOrDefault(bundle, key, key, params);
    if (key.equals(value)) return null;
    return value;
  }

  @NotNull
  public static String getCancelButtonText() {
    return message("button.cancel");
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

  @Deprecated
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

  public static void loadBundleFromPlugin(@Nullable ClassLoader pluginClassLoader) {
    ResourceBundle bundle = loadLanguageBundle(pluginClassLoader, BUNDLE);
    if (bundle != null) ourBundle = bundle;
  }
}