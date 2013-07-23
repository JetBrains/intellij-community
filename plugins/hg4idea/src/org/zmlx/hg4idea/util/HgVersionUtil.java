package org.zmlx.hg4idea.util;

import org.zmlx.hg4idea.command.HgVersionCommand;

/**
 * @author Nadya Zabrodina
 */
public class HgVersionUtil {
  private static Double myHgVersion = null;


  public static void updateHgVersion(String executable, boolean isRunViaBash) {
    myHgVersion = new HgVersionCommand().getVersion(executable, isRunViaBash);
  }

  public static boolean isAmendSupported() {
    return myHgVersion!=null &&  myHgVersion >= 2.2; // amend commit supported only for 2.2 or later
  }

  public static boolean isValid(String executable, boolean isRunViaBash) {
    updateHgVersion(executable, isRunViaBash);
    return myHgVersion != null;
  }
}
