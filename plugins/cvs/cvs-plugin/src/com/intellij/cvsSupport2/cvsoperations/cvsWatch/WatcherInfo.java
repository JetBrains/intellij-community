/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsWatch;

import com.intellij.openapi.util.text.StringUtil;

/**
 * author: lesya
 */
public class WatcherInfo {
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
