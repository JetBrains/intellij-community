package com.intellij.cvsSupport2.cvsoperations.cvsContent;


import java.io.File;


class DirectoryContentListener {
  private String myModulePath;
  private DirectoryContent myDirectoryContent = new DirectoryContent();
  private static final String FILE_MESSAGE_PREFIX = "fname ";
  private static final String MODULE_MESSAGE_PREFIX = "cvs server: ignoring module ";

  public void messageSent(String message) {
    if (directoryMessage(message)){
      String directoryName = directoryNameFromMessage(message);
      if (myModulePath != null) directoryName = myModulePath + "/" + new File(directoryName).getName();
      myDirectoryContent.addSubDirectory(directoryName);
    }
    else if (fileMessage(message)) {
      String fileName = fileNameFromMessage(message);
      if (myModulePath != null) fileName = myModulePath + "/" + new File(fileName).getName();
      myDirectoryContent.addFile(fileName);
    } else if (moduleMessage(message)){
      String moduleName = moduleNameFromMessage(message);
      myDirectoryContent.addModule(moduleName);
    }
  }

  private String moduleNameFromMessage(String message) {
    return message.substring(MODULE_MESSAGE_PREFIX.length());
  }

  public static boolean moduleMessage(String message) {
    return message.startsWith(MODULE_MESSAGE_PREFIX);
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
    return message.startsWith("cvs server: New directory")
           || message.startsWith("cvs update: New directory");
  }

  public static String directoryNameFromMessage(String message) {
    byte directoryNameBeginMarker = '`';
    byte directoryNameendMarker = '\'';
    int beginIndex = message.indexOf(directoryNameBeginMarker) + 1;
    int endIndex = message.indexOf(directoryNameendMarker);
    return message.substring(beginIndex, endIndex);
  }

  public DirectoryContent getDirectoryContent() { return myDirectoryContent; }

  //public void addModule(String name) {
  //  myDirectoryContent.addModule(name);
  //}
}


