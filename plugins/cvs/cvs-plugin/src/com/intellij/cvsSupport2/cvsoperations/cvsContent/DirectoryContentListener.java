/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsContent;


import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.regex.Pattern;


class DirectoryContentListener {
  private String myModulePath;
  private final DirectoryContent myDirectoryContent = new DirectoryContent();
  @NonNls private static final String FILE_MESSAGE_PREFIX = "fname ";
  @NonNls private static final String MODULE_MESSAGE_PREFIX = "cvs server: ignoring module ";
  @NonNls private static final String MODULE_MESSAGE_PREFIX_2 = "cvs server: Updating ";
  @NonNls private static final Pattern NEW_DIRECTORY_PATTERN = Pattern.compile("cvs .*: New directory.*-- ignored");
  private String myModuleName;

  public void messageSent(String message) {
    if (directoryMessage(message)) {
      String directoryName = directoryNameFromMessage(message);
      if (myModulePath != null) directoryName = myModulePath + "/" + new File(directoryName).getName();
      myDirectoryContent.addSubDirectory(directoryName);
    }
    else if (fileMessage(message)) {
      String fileName = fileNameFromMessage(message);
      if ((myModuleName != null) && StringUtil.startsWithConcatenation(fileName, myModuleName, "/")) {
        fileName = fileName.substring(myModuleName.length() + 1);
      }
      final int slashPos = fileName.indexOf('/');
      if (slashPos > 0) {
        final String directoryName = fileName.substring(0, slashPos);
        myDirectoryContent.addSubDirectory(directoryName);
      }
      else {
        if (myModulePath != null) fileName = myModulePath + "/" + new File(fileName).getName();
        myDirectoryContent.addFile(fileName);
      }
    }
    else if (moduleMessage_ver1(message)) {
      final String moduleName = moduleNameFromMessage_ver1(message);
      myDirectoryContent.addModule(moduleName);
    }
    else if (moduleMessage_ver2(message)) {
      final String moduleName = moduleNameFromMessage_ver2(message);
      myDirectoryContent.addModule(moduleName);
    }
  }

  private String moduleNameFromMessage_ver2(final String message) {
    final String prefix = updatingModulePrefix2();
    return message.substring(prefix.length());
  }

  public String getModuleName() {
    return myModuleName;
  }

  private static String moduleNameFromMessage_ver1(String message) {
    return message.substring(MODULE_MESSAGE_PREFIX.length());
  }

  public static boolean moduleMessage_ver1(String message) {
    return message.startsWith(MODULE_MESSAGE_PREFIX);
  }

  public boolean moduleMessage_ver2(String message) {
    if (myModuleName == null) {
      return false;
    }
    return message.startsWith(updatingModulePrefix2());
  }

  private String updatingModulePrefix2() {
    return MODULE_MESSAGE_PREFIX_2 + myModuleName + "/";
  }

  public static String fileNameFromMessage(String message) {
    return message.substring(FILE_MESSAGE_PREFIX.length());
  }

  public void setModulePath(String modulePath) {
    myModulePath = modulePath;
  }

  public static boolean fileMessage(String message) {
    return message.startsWith(FILE_MESSAGE_PREFIX);
  }

  public static boolean directoryMessage(String message) {
    return NEW_DIRECTORY_PATTERN.matcher(message).matches();
  }

  public static String directoryNameFromMessage(String message) {
    final byte directoryNameBeginMarker = '`';
    final byte directoryNameEndMarker = '\'';
    final int beginIndex = message.indexOf(directoryNameBeginMarker) + 1;
    final int endIndex = message.indexOf(directoryNameEndMarker);
    return message.substring(beginIndex, endIndex);
  }

  public DirectoryContent getDirectoryContent() {
    return myDirectoryContent;
  }

  public void setModuleName(final String moduleLocation) {
    myModuleName = moduleLocation;
  }
}


