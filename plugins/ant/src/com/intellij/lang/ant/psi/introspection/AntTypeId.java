package com.intellij.lang.ant.psi.introspection;

import com.intellij.lang.ant.misc.AntStringInterner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class AntTypeId {
  private String myName;
  private String myNsPrefix;

  public AntTypeId(@NonNls final String name, @Nullable final String nsPrefix) {
    init(name, nsPrefix);
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
    if (myNsPrefix != null && antTypeId.myNsPrefix != null && !myNsPrefix.equals(antTypeId.myNsPrefix)) return false;

    return true;
  }

  public int hashCode() {
    return 31 * myName.hashCode() + (myNsPrefix != null ? myNsPrefix.hashCode() : 0);
  }

  @NonNls
  public String getName() {
    return myName;
  }

  @Nullable
  public String getNamespacePrefix() {
    return myNsPrefix;
  }

  private void init(@NonNls final String name, @Nullable final String nsPrefix) {
    myName = AntStringInterner.intern(name);
    myNsPrefix = nsPrefix;
    if (nsPrefix != null) {
      myNsPrefix = (nsPrefix.length() > 0) ? AntStringInterner.intern(nsPrefix) : null;
    }
  }
}
