/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.external;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.StringProperty;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;

abstract class BaseExternalTool implements DiffTool {
  private final BooleanProperty myEnableProperty;
  private final StringProperty myToolProperty;

  protected BaseExternalTool(BooleanProperty enableProperty, StringProperty toolProperty) {
    myEnableProperty = enableProperty;
    myToolProperty = toolProperty;
  }

  public boolean canShow(DiffRequest request) {
    AbstractProperty.AbstractPropertyContainer config = DiffManagerImpl.getInstanceEx().getProperties();
    if (!myEnableProperty.value(config)) return false;
    String path = getToolPath();
    if (path == null || path.length() == 0) return false;
    DiffContent[] contents = request.getContents();
    if (contents.length != 2) return false;
    if (externalize(request, 0) == null) return false;
    if (externalize(request, 1) == null) return false;
    return true;
  }

  @Override
  public DiffViewer createComponent(String title, DiffRequest request, Window window, Disposable parentDisposable) {
    return null;
  }

  protected abstract ContentExternalizer externalize(DiffRequest request, int index);

  private String getToolPath() {
    return myToolProperty.get(DiffManagerImpl.getInstanceEx().getProperties());
  }

  public void show(DiffRequest request) {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(getToolPath());
    try {
      commandLine.addParameter(convertToPath(request, 0));
      commandLine.addParameter(convertToPath(request, 1));
      commandLine.createProcess();
    }
    catch (Exception e) {
      ExecutionErrorDialog.show(new ExecutionException(e.getMessage()),
                                DiffBundle.message("cant.launch.diff.tool.error.message"), request.getProject());
    }
  }

  private String convertToPath(DiffRequest request, int index) throws IOException {
    return externalize(request, index).getContentFile().getAbsolutePath();
  }

  @Nullable
  protected static VirtualFile getLocalFile(VirtualFile file) {
    if (file != null && file.isInLocalFileSystem()) return file;
    return null;
  }

  protected interface ContentExternalizer {
    File getContentFile() throws IOException;
  }

  protected static class LocalFileExternalizer implements ContentExternalizer {
    private final File myFile;

    public LocalFileExternalizer(File file) {
      myFile = file;
    }

    public File getContentFile() {
      return myFile;
    }

    @Nullable
    public static LocalFileExternalizer tryCreate(VirtualFile file) {
      if (file == null || !file.isValid()) return null;
      if (!file.isInLocalFileSystem()) return null;
      return new LocalFileExternalizer(new File(file.getPath().replace('/', File.separatorChar)));
    }
  }
}
