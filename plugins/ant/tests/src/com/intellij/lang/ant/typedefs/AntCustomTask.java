/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.lang.ant.typedefs;

import org.apache.tools.ant.Task;

public class AntCustomTask extends Task {

  private String myString;
  private int myInteger;
  private boolean myBoolean;


  public void setString(final String string) {
    myString = string;
  }

  public void setInteger(final int integer) {
    myInteger = integer;
  }

  public void setBoolean(final boolean aBoolean) {
    myBoolean = aBoolean;
  }
}
