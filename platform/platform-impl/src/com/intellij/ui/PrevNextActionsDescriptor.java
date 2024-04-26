// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.ui;

public final class PrevNextActionsDescriptor {
  private final String myPrevActionId;
  private final String myNextActionId;

  public PrevNextActionsDescriptor(final String nextActionId, final String prevActionId) {
    myNextActionId = nextActionId;
    myPrevActionId = prevActionId;
  }

  public String getNextActionId() {
    return myNextActionId;
  }

  public String getPrevActionId() {
    return myPrevActionId;
  }
}