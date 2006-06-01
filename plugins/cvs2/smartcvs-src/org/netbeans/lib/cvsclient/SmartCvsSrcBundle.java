/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Sep 5, 2005
 * Time: 5:09:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class SmartCvsSrcBundle {
  private static Reference<ResourceBundle> ourBundle;

  @NonNls protected static final String PATH_TO_BUNDLE = "org.netbeans.lib.cvsclient.SmartCvsSrcBundle";

  private SmartCvsSrcBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = PATH_TO_BUNDLE)String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }
}
