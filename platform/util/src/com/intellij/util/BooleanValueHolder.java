// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

public final class BooleanValueHolder {
  private boolean myValue;

  public BooleanValueHolder(boolean value) {
    myValue = value;
  }

  public boolean getValue(){
    return myValue;
  }

  public void setValue(boolean value){
    myValue = value;
  }

}
