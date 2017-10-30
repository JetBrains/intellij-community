// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.*;

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
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }

  public static String messageOrDefault(@Nullable ResourceBundle bundle, @NotNull String key, @Nullable String defaultValue, @NotNull Object... params) {
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