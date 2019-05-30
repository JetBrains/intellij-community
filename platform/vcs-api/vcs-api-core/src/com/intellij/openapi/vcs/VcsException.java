// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.Collections.singleton;

public class VcsException extends Exception {
  public static final VcsException[] EMPTY_ARRAY = new VcsException[0];

  private VirtualFile myVirtualFile;
  private Collection<String> myMessages;
  private boolean isWarning = false;

  public VcsException(String message) {
    super(message);
    initMessage(message);
  }

  private void initMessage(@Nullable String message) {
    myMessages = singleton(prepareMessage(message));
  }

  @NotNull
  private static String prepareMessage(@Nullable String message) {
    return notNull(message, message("exception.text.unknown.error"));
  }

  public VcsException(Throwable throwable, boolean isWarning) {
    this(getMessage(throwable), throwable);
    this.isWarning = isWarning;
  }

  public VcsException(Throwable throwable) {
    this(throwable, false);
  }

  public VcsException(String message, Throwable cause) {
    super(message, cause);
    initMessage(message);
  }

  public VcsException(String message, boolean isWarning) {
    this(message);
    this.isWarning = isWarning;
  }

  public VcsException(@NotNull Collection<String> messages) {
    myMessages = map(messages, VcsException::prepareMessage);
  }

  //todo: should be in constructor?
  public void setVirtualFile(VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @NotNull
  public String[] getMessages() {
    return ArrayUtilRt.toStringArray(myMessages);
  }

  public VcsException setIsWarning(boolean warning) {
    isWarning = warning;
    return this;
  }

  public boolean isWarning() {
    return isWarning;
  }

  @Override
  @NotNull
  public String getMessage() {
    return join(myMessages, ", ");
  }

  @Nullable
  public static String getMessage(@Nullable Throwable throwable) {
    return throwable != null ? chooseNotNull(throwable.getMessage(), throwable.getLocalizedMessage()) : null;
  }
}
