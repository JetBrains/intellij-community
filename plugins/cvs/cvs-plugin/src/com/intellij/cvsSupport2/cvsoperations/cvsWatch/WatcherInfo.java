// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsoperations.cvsWatch;

import com.intellij.openapi.util.text.StringUtil;

/**
 * author: lesya
 */
public final class WatcherInfo {
  private final String myFile;
  private final String myUser;
  private final String myActions;

  public static WatcherInfo createOn(String string){
    String[] strings = string.split("\t");
    if (strings.length < 2) return null;
    return new WatcherInfo(strings[0], strings[1], StringUtil.join(strings, ", "));
  }

  private WatcherInfo(String file, String user, String actions) {
    myFile = file;
    myUser = user;
    myActions = actions;
  }

  public String getFile() { return myFile; }

  public String getUser() { return myUser; }

  public String getActions() { return myActions; }
}
