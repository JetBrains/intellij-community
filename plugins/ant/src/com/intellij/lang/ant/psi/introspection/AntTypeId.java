package com.intellij.lang.ant.psi.introspection;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

public class AntTypeId {

  private final Pair<String, String> myId;

  public AntTypeId(@NonNls final String name, @NonNls final String namespace) {
    myId = new Pair<String, String>(name, namespace);
  }

  public AntTypeId(@NonNls final String name) {
    this(name, null);
  }

  public int hashCode() {
    return myId.hashCode();
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof AntTypeId)) return false;
    AntTypeId right = (AntTypeId)obj;
    return myId.getFirst().equals(right.myId.getFirst()) &&
           (myId.getSecond() == null || right.myId.getSecond() == null || myId.getSecond().equals(right.myId.getSecond()));
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
