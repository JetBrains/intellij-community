// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

public class ParamInfo {

  public String name;
  public String type;

  @SuppressWarnings("unused")
  public ParamInfo() {}

  public ParamInfo(String name, String type) {
    this.name = name;
    this.type = type;
  }

  public final int hashCode() {
    int hashCode = 0;
    if (name != null) {
      hashCode += name.hashCode();
    }
    if (type != null) {
      hashCode += type.hashCode();
    }
    return hashCode;
  }

  public String toString() {
    return "<" + name + "," + type + ">";
  }
}
