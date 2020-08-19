// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsContexts;

import java.util.Date;

public class LogicalLock {
  private final boolean myIsLocal;
  @NlsContexts.DetailedDescription private final String myOwner;
  @NlsContexts.DetailedDescription private final String myComment;
  private final Date myCreationDate;
  private final Date myExpirationDate;

  public LogicalLock(boolean isLocal,
                     @NlsContexts.DetailedDescription String owner,
                     @NlsContexts.DetailedDescription String comment,
                     Date creationDate,
                     Date expirationDate) {
    myIsLocal = isLocal;
    myOwner = owner;
    myComment = comment;
    myCreationDate = creationDate;
    myExpirationDate = expirationDate;
  }

  @NlsContexts.DetailedDescription
  public String getOwner() {
    return myOwner;
  }

  @NlsContexts.DetailedDescription
  public String getComment() {
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
