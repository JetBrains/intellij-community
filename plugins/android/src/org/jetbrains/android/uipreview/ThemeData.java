package org.jetbrains.android.uipreview;

import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
public class ThemeData implements Comparable<ThemeData> {
  private final String myName;
  private final boolean myProjectTheme;

  public ThemeData(@NotNull String name, boolean projectTheme) {
    myName = name;
    myProjectTheme = projectTheme;
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ThemeData myTheme = (ThemeData)o;

    if (myProjectTheme != myTheme.myProjectTheme) return false;
    if (!myName.equals(myTheme.myName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + (myProjectTheme ? 1 : 0);
    return result;
  }

  @Override
  public int compareTo(ThemeData theme) {
    return myName.compareTo(theme.myName);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isProjectTheme() {
    return myProjectTheme;
  }
}
