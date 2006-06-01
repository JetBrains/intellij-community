package com.intellij;/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

public class CvsBundle {
  private static Reference<ResourceBundle> ourBundle;

  @NonNls private static final String BUNDLE_NAME = "com.intellij.cvsSupport2.CvsBundle";

  private CvsBundle() {
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

  public static String message(@PropertyKey(resourceBundle = BUNDLE_NAME)String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE_NAME);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }
}
