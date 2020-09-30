// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.openapi.util.NlsSafe;

public class JavaFxManifestAttribute {
  private String myName;
  private String myValue;

  public JavaFxManifestAttribute() {
  }

  public JavaFxManifestAttribute(String name, String value) {
    myName = name;
    myValue = value;
  }

  @NlsSafe
  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  @NlsSafe
  public String getValue() {
    return myValue;
  }

  public void setValue(String value) {
    myValue = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JavaFxManifestAttribute attribute = (JavaFxManifestAttribute)o;

    if (!myName.equals(attribute.myName)) return false;
    if (!myValue.equals(attribute.myValue)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myValue.hashCode();
    return result;
  }
}
