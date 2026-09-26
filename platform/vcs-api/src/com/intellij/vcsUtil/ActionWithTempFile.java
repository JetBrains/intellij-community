// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

public abstract class ActionWithTempFile {
  private File myTempFile;
  private final File mySourceFile;
  private static final @NonNls String TMP_PREFIX = "vcs";
  private static final @NonNls String TMP_SUFFIX = "tmp";

  public ActionWithTempFile(final File sourceFile){
    mySourceFile = sourceFile;
  }

  public void execute () throws VcsException {
    try {
      try {
        init();
        executeInternal();
      }
      finally {
        rollbackChanges();
      }
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  private void init() throws IOException {
    myTempFile = FileUtil.createTempFile(TMP_PREFIX, TMP_SUFFIX);
    FileUtil.delete(myTempFile);
    FileUtil.rename(mySourceFile, myTempFile);
  }

  protected abstract void executeInternal() throws VcsException;

  private void rollbackChanges() throws IOException {
    try {
      FileUtil.delete(mySourceFile);
    }
    finally {
      try {
        FileUtil.rename(myTempFile, mySourceFile);
      }
      finally {
        FileUtil.delete(myTempFile);
      }
    }
  }
}
