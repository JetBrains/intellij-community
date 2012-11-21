package org.jetbrains.android.refactoring;

import com.android.SdkConstants;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidAttributeInfo {
  private final String myName;
  private final String myPackage;

  public AndroidAttributeInfo(@NotNull String name, @Nullable String aPackage) {
    myName = name;
    myPackage = aPackage;
  }

  public String getNamespace() {
    final boolean system = AndroidUtils.SYSTEM_RESOURCE_PACKAGE.equals(myPackage);
    return system ? SdkConstants.NS_RESOURCES : null;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public String getPackage() {
    return myPackage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AndroidAttributeInfo info = (AndroidAttributeInfo)o;

    if (!myName.equals(info.myName)) {
      return false;
    }
    if (myPackage != null ? !myPackage.equals(info.myPackage) : info.myPackage != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + (myPackage != null ? myPackage.hashCode() : 0);
    return result;
  }

  @NotNull
  public String getAttributeId() {
    return myPackage != null ? myPackage + ":" + myName : myName;
  }

  @Override
  public String toString() {
    return getAttributeId();
  }
}
