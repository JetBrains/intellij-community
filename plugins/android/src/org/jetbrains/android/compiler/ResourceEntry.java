package org.jetbrains.android.compiler;

import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
public class ResourceEntry {
  private final String myType;
  private final String myName;

  ResourceEntry(@NotNull String type, @NotNull String name) {
    myType = type;
    myName = name;
  }

  @NotNull
  public String getType() {
    return myType;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ResourceEntry entry = (ResourceEntry)o;

    if (!myName.equals(entry.myName)) return false;
    if (!myType.equals(entry.myType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myType.hashCode();
    result = 31 * result + myName.hashCode();
    return result;
  }
}
