package com.intellij.cvsSupport2.cvsoperations.cvsWatch;



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
    return new WatcherInfo(strings[0], strings[1], createActionsOn(strings));
  }

  private static String createActionsOn(String[] strings) {
    StringBuffer result = new StringBuffer();
    for (int i = 2; i < strings.length; i++) {
      result.append(strings[i]);
      if (i < strings.length - 1) result.append(", ");
    }
    return result.toString();
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
