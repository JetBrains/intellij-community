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

  public String getLinuxIcon() {
    return myLinuxIcon;
  }

  public String getLinuxIcon(String relativeToPath) {
    return getRelativeIcon(relativeToPath, myLinuxIcon);
  }

  public void setLinuxIcon(String linuxIcon) {
    myLinuxIcon = linuxIcon;
  }

  public String getMacIcon() {
    return myMacIcon;
  }

  public String getMacIcon(String relativeToPath) {
    return getRelativeIcon(relativeToPath, myMacIcon);
  }

  public void setMacIcon(String macIcon) {
    myMacIcon = macIcon;
  }

  public String getWindowsIcon() {
    return myWindowsIcon;
  }

  public String getWindowsIcon(String relativeToPath) {
    return getRelativeIcon(relativeToPath, myWindowsIcon);
  }

  public void setWindowsIcon(String windowsIcon) {
    myWindowsIcon = windowsIcon;
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

    return true;
  }

  @Override
  public int hashCode() {
    int result = myLinuxIcon != null ? myLinuxIcon.hashCode() : 0;
    result = 31 * result + (myMacIcon != null ? myMacIcon.hashCode() : 0);
    result = 31 * result + (myWindowsIcon != null ? myWindowsIcon.hashCode() : 0);
    return result;
  }

  @Nullable
  private static String getRelativeIcon(String relativeToPath, String icon) {
    if (!StringUtil.isEmpty(icon) && !StringUtil.isEmpty(relativeToPath)) {
      return FileUtil.getRelativePath(relativeToPath, icon, '/');
    }
    return icon;
  }
}
