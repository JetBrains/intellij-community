// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.IOException;

@ApiStatus.Internal
public abstract class AbstractTestWithTextFile {
  public static final String BASE_DIRECTORY = "platform/vcs-log/graph/testData/";

  protected final String myDirectory;

  protected AbstractTestWithTextFile(String directory) {
    this.myDirectory = PathManagerEx.findFileUnderCommunityHome(BASE_DIRECTORY + directory).getPath();
  }

  protected String loadText(String filename) throws IOException {
    return FileUtil.loadFile(new File(myDirectory, filename), true);
  }
}
