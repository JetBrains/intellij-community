// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import org.jetbrains.annotations.NotNull;

import java.util.Date;

public final class VcsRevisionDescriptionImpl implements VcsRevisionDescription {
  private final VcsRevisionNumber myNumber;
  private Date myDate;
  private String myAuthor;
  private String myMessage;

  public VcsRevisionDescriptionImpl(@NotNull VcsRevisionNumber number, Date date, String author, String message) {
    myNumber = number;
    myDate = date;
    myAuthor = author;
    myMessage = message;
  }

  public VcsRevisionDescriptionImpl(VcsRevisionNumber number) {
    myNumber = number;
  }

  @Override
  public @NotNull VcsRevisionNumber getRevisionNumber() {
    return myNumber;
  }

  @Override
  public Date getRevisionDate() {
    return myDate;
  }

  @Override
  public String getAuthor() {
    return myAuthor;
  }

  @Override
  public String getCommitMessage() {
    return myMessage;
  }

  public void setDate(Date date) {
    myDate = date;
  }

  public void setAuthor(String author) {
    myAuthor = author;
  }

  public void setMessage(String message) {
    myMessage = message;
  }
}
