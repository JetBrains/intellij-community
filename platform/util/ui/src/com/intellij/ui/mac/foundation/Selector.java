// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.foundation;

import com.sun.jna.NativeLong;

/**
 * @author spleaner
 */
public class Selector extends NativeLong {

  private String myName;

  public Selector() {
    this("undefined selector", 0);
  }

  public Selector(String name, long value) {
    super(value);
    myName = name;
  }

  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return String.format("[Selector %s]", myName);
  }

  public Selector initName(final String name) {
    myName = name;
    return this;
  }
}
