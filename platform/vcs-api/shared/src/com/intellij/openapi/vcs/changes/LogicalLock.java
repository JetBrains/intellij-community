// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;

import java.util.Date;

public final class LogicalLock {
  private final boolean myIsLocal;
  private final String myOwner;
  private final String myComment;
  private final Date myCreationDate;
  private final Date myExpirationDate;

  public LogicalLock(boolean isLocal, String owner, String comment, Date creationDate, Date expirationDate) {
    myIsLocal = isLocal;
    myOwner = owner;
    myComment = comment;
    myCreationDate = creationDate;
    myExpirationDate = expirationDate;
  }

  public @NlsSafe String getOwner() {
    return myOwner;
  }

  public @NlsSafe String getComment() {
    return myComment;
  }

  public Date getCreationDate() {
    return myCreationDate;
  }

  public Date getExpirationDate() {
    return myExpirationDate;
  }

  public boolean isIsLocal() {
    return myIsLocal;
  }
}
