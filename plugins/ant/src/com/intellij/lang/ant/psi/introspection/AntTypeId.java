package com.intellij.lang.ant.psi.introspection;

import com.intellij.lang.ant.misc.AntStringInterner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class AntTypeId {
  private String myName;
  private String myNamespace;

  public AntTypeId(@NonNls final String name, @Nullable final String namespace) {
    init(name, namespace);
  }

  public AntTypeId(@NonNls final String name) {
    final int nsSeparator = name.indexOf(':');
    if (nsSeparator < 0) {
      init(name, null);
    }
    else {
      init(name.substring(nsSeparator + 1), name.substring(0, nsSeparator));
    }
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final AntTypeId antTypeId = (AntTypeId)o;

    if (!myName.equals(antTypeId.myName)) return false;
    if (myNamespace != null && antTypeId.myNamespace != null && !myNamespace.equals(antTypeId.myNamespace)) return false;

    return true;
  }

  public int hashCode() {
    return 31 * myName.hashCode() + (myNamespace != null ? myNamespace.hashCode() : 0);
  }

  @NonNls
  public String getName() {
    return myName;
  }

  @Nullable
  public String getNamespace() {
    return myNamespace;
  }

  private void init(@NonNls final String name, @Nullable final String namespace) {
    myName = AntStringInterner.intern(name);
    myNamespace = namespace;
    if (namespace != null) {
      myNamespace = (namespace.length() > 0) ? AntStringInterner.intern(namespace) : null;
    }
  }
}
