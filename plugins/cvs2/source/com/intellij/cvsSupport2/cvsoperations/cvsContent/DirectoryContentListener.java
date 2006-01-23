package com.intellij.cvsSupport2.cvsoperations.cvsContent;


import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.regex.Pattern;


class DirectoryContentListener {
  private String myModulePath;
  private DirectoryContent myDirectoryContent = new DirectoryContent();
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
      if (myModulePath != null) fileName = myModulePath + "/" + new File(fileName).getName();
      myDirectoryContent.addFile(fileName);
    }
    else if (moduleMessage_ver1(message)) {
      String moduleName = moduleNameFromMessage_ver1(message);
      myDirectoryContent.addModule(moduleName);
    }
    else if (moduleMessage_ver2(message)) {
      String moduleName = moduleNameFromMessage_ver2(message);
      myDirectoryContent.addModule(moduleName);
    }
  }

  private String moduleNameFromMessage_ver2(final String message) {
    String prefix = updatingModulePrefix2();
    return message.substring(prefix.length());
  }

  private String moduleNameFromMessage_ver1(String message) {
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
    return new File(message.substring(FILE_MESSAGE_PREFIX.length())).getName();
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
    byte directoryNameBeginMarker = '`';
    byte directoryNameendMarker = '\'';
    int beginIndex = message.indexOf(directoryNameBeginMarker) + 1;
    int endIndex = message.indexOf(directoryNameendMarker);
    return message.substring(beginIndex, endIndex);
  }


  public DirectoryContent getDirectoryContent() {
    return myDirectoryContent;
  }

  //public void addModule(String name) {
  //  myDirectoryContent.addModule(name);
  //}

  public void setModuleName(final String moduleLocation) {
    myModuleName = moduleLocation;
  }
}


