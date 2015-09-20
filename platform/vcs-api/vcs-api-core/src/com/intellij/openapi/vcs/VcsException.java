/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class VcsException extends Exception {
  public static final VcsException[] EMPTY_ARRAY = new VcsException[0];

  private VirtualFile myVirtualFile;
  private Collection<String> myMessages;
  private boolean isWarning = false;

  public VcsException(String message) {
    super(message);
    initMessage(message);
  }

  private void initMessage(final String message) {
    String shownMessage = message == null ? VcsBundle.message("exception.text.unknown.error") : message;
    myMessages = Collections.singleton(shownMessage);
  }

  public VcsException(Throwable throwable, final boolean isWarning) {
    this(getMessage(throwable), throwable);
    this.isWarning = isWarning;
  }

  public VcsException(Throwable throwable) {
    this(throwable, false);
  }

  public VcsException(final String message, final Throwable cause) {
    super(message, cause);
    initMessage(message);
  }

  public VcsException(final String message, final boolean isWarning) {
    this(message);
    this.isWarning = isWarning;
  }

  public VcsException(Collection<String> messages) {
    myMessages = messages;
  }

  //todo: should be in constructor?
  public void setVirtualFile(VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public String[] getMessages() {
    return ArrayUtil.toStringArray(myMessages);
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
    return StringUtil.join(myMessages, ", ");
  }

  @Nullable
  public static String getMessage(@Nullable Throwable throwable) {
    return throwable != null ? ObjectUtils.chooseNotNull(throwable.getMessage(), throwable.getLocalizedMessage()) : null;
  }
}
