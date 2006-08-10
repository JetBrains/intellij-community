package com.intellij.lang.ant.psi.introspection;

import org.jetbrains.annotations.NonNls;

public class AntTypeId {
  private String myName;
  private String myNamespace;

  public AntTypeId(@NonNls final String name, @NonNls final String namespace) {
    myName = name;
    myNamespace = namespace;
  }

  public AntTypeId(@NonNls final String name) {
    this(name, null);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final AntTypeId antTypeId = (AntTypeId)o;

    if (!myName.equals(antTypeId.myName)) return false;
    if (myNamespace != null ? !myNamespace.equals(antTypeId.myNamespace) : antTypeId.myNamespace != null) return false;

    return true;
  }

  public int hashCode() {
    return 31 * myName.hashCode() + (myNamespace != null ? myNamespace.hashCode() : 0);
  }

  @NonNls
  public String getName() {
    return myName;
  }

  @NonNls
  public String getNamespace() {
    return myNamespace;
  }
}
