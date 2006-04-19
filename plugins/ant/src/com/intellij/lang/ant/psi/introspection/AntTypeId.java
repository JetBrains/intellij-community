package com.intellij.lang.ant.psi.introspection;

import com.intellij.lang.ant.AntDefaultNSProvider;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

public class AntTypeId {

  private final Pair<String, String> myId;

  public AntTypeId(@NonNls final String name, @NonNls final String namespace) {
    myId = new Pair<String, String>(name, namespace);
  }

  public AntTypeId(@NonNls final String name) {
    this(name, AntDefaultNSProvider.getNamespace());
  }

  public int hashCode() {
    return myId.hashCode();
  }

  public boolean equals(Object obj) {
    return obj instanceof AntTypeId && myId.equals(((AntTypeId)obj).myId);
  }

  @NonNls
  public String getName() {
    return myId.first;
  }

  @NonNls
  public String getNamespace() {
    return myId.second;
  }
}
