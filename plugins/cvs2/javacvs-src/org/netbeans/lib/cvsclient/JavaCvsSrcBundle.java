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
 * Time: 3:20:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class JavaCvsSrcBundle {
  @NonNls protected static final String PATH_TO_BUNDLE = "org.netbeans.lib.cvsclient.JavaCvsSrcBundle";
  private final static ResourceBundle ourResourceBundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);

  public static String message(@PropertyKey(resourceBundle = "org.netbeans.lib.cvsclient.JavaCvsSrcBundle") String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
