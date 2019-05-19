// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.Utils;
import org.editorconfig.core.DefaultParserCallback;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EditorConfigFilesCollector extends DefaultParserCallback {
  private final List<String> myEditorConfigFilePaths = new ArrayList<>();

  @Override
  public boolean processEditorConfig(File configFile) {
    myEditorConfigFilePaths.add(configFile.getPath());
    return true;
  }

  @Override
  public boolean processFile(File file) {
    myEditorConfigFilePaths.clear();
    return true;
  }

  public List<String> getFilePaths() {
    return myEditorConfigFilePaths;
  }

  @NotNull
  public List<VirtualFile> getEditorConfigFiles() {
    return Utils.pathsToFiles(myEditorConfigFilePaths);
  }

}
