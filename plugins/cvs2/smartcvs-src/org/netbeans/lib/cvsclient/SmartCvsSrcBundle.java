/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;
import com.intellij.CommonBundle;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Sep 5, 2005
 * Time: 5:09:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class SmartCvsSrcBundle {
  @NonNls protected static final String PATH_TO_BUNDLE = "org.netbeans.lib.cvsclient.SmartCvsSrcBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  public static String message(@PropertyKey(resourceBundle = "org.netbeans.lib.cvsclient.SmartCvsSrcBundle") String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
