package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxApplicationIcons {
  private String myLinuxIcon;
  private String myMacIcon;
  private String myWindowsIcon;
  private String myBaseDir;

  public String getLinuxIcon() {
    return myLinuxIcon;
  }

  public String getLinuxIcon(boolean isRelative) {
    return isRelative ? getRelativeIcon(myLinuxIcon) : myLinuxIcon;
  }

  public void setLinuxIcon(String linuxIcon) {
    myLinuxIcon = linuxIcon;
  }

  public String getMacIcon() {
    return myMacIcon;
  }

  public String getMacIcon(boolean isRelative) {
    return isRelative ? getRelativeIcon(myMacIcon) : myMacIcon;
  }

  public void setMacIcon(String macIcon) {
    myMacIcon = macIcon;
  }

  public String getWindowsIcon() {
    return myWindowsIcon;
  }

  public String getWindowsIcon(boolean isRelative) {
    return isRelative ? getRelativeIcon(myWindowsIcon) : myWindowsIcon;
  }

  public void setWindowsIcon(String windowsIcon) {
    myWindowsIcon = windowsIcon;
  }

  public String getBaseDir() {
    return myBaseDir;
  }

  public void setBaseDir(String baseDir) {
    myBaseDir = baseDir;
  }

  public boolean isEmpty() {
    return StringUtil.isEmpty(myLinuxIcon) && StringUtil.isEmpty(myMacIcon) && StringUtil.isEmpty(myWindowsIcon);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JavaFxApplicationIcons icons = (JavaFxApplicationIcons)o;

    if (myLinuxIcon != null ? !myLinuxIcon.equals(icons.myLinuxIcon) : icons.myLinuxIcon != null) return false;
    if (myMacIcon != null ? !myMacIcon.equals(icons.myMacIcon) : icons.myMacIcon != null) return false;
    if (myWindowsIcon != null ? !myWindowsIcon.equals(icons.myWindowsIcon) : icons.myWindowsIcon != null) return false;
    if (myBaseDir != null ? !myBaseDir.equals(icons.myBaseDir) : icons.myBaseDir != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myLinuxIcon != null ? myLinuxIcon.hashCode() : 0;
    result = 31 * result + (myMacIcon != null ? myMacIcon.hashCode() : 0);
    result = 31 * result + (myWindowsIcon != null ? myWindowsIcon.hashCode() : 0);
    result = 31 * result + (myBaseDir != null ? myBaseDir.hashCode() : 0);
    return result;
  }

  @Nullable
  private String getRelativeIcon(String icon) {
    if (!StringUtil.isEmpty(icon) && !StringUtil.isEmpty(myBaseDir)) {
      return FileUtil.getRelativePath(myBaseDir, icon, '/');
    }
    return icon;
  }
}
