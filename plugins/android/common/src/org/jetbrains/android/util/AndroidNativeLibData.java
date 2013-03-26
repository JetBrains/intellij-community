package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidNativeLibData {
  private final String myPath;
  private final String myArchitecture;
  private final String myTargetFileName;

  public AndroidNativeLibData(@NotNull String architecture, @NotNull String path, @NotNull String targetFileName) {
    myPath = path;
    myArchitecture = architecture;
    myTargetFileName = targetFileName;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @NotNull
  public String getArchitecture() {
    return myArchitecture;
  }

  @NotNull
  public String getTargetFileName() {
    return myTargetFileName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AndroidNativeLibData lib = (AndroidNativeLibData)o;

    if (!myArchitecture.equals(lib.myArchitecture)) return false;
    if (!myTargetFileName.equals(lib.myTargetFileName)) return false;
    if (!myPath.equals(lib.myPath)) return false;

    return true;
  }

  @Override
  public String toString() {
    return "[" + myPath + "," + myArchitecture + "," + myTargetFileName + "]";
  }

  @Override
  public int hashCode() {
    int result = myPath.hashCode();
    result = 31 * result + myArchitecture.hashCode();
    result = 31 * result + myTargetFileName.hashCode();
    return result;
  }
}
