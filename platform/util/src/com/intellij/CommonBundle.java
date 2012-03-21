/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * @author yole
 */
public class CommonBundle extends BundleBase {
  public static boolean assertKeyIsFound = false;

  @NonNls private static final String BUNDLE = "messages.CommonBundle";
  private static Reference<ResourceBundle> ourBundle;

  private CommonBundle() {}

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return message(getCommonBundle(), key, params);
  }

  private static ResourceBundle getCommonBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
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

  public static String getOkButtonText(){
    return message("button.ok");
  }

  public static String getYesButtonText(){
    return message("button.yes");
  }

  public static String getNoButtonText(){
    return message("button.no");
  }

  public static String getContinueButtonText(){
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
}
