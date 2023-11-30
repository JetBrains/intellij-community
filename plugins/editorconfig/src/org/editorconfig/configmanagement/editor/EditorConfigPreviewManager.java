// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.editor;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service(Service.Level.PROJECT)
@State(
  name = "EditorConfigPreviewManager",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public final class EditorConfigPreviewManager implements PersistentStateComponent<Element> {
  private final Map<String, String> myPreviewMap = new HashMap<>();

  public static final String PREVIEW_RECORD_TAG     = "editorConfig";
  public static final String EDITORCONFIG_FILE_ATTR = "file";
  public static final String PREVIEW_FILE_ATTR      = "previewFile";

  @Override
  public @NotNull Element getState() {
    Element state = new Element("previewData");
    for (String key : myPreviewMap.keySet()) {
      String value = myPreviewMap.get(key);
      if (value != null) {
        Element previewRec = new Element(PREVIEW_RECORD_TAG);
        previewRec.setAttribute(EDITORCONFIG_FILE_ATTR, key);
        previewRec.setAttribute(PREVIEW_FILE_ATTR, value);
        state.addContent(previewRec);
      }
    }
    return state;
  }

  @Override
  public void loadState(@NotNull Element state) {
    for (Element previewRec : state.getChildren(PREVIEW_RECORD_TAG)) {
      String key = previewRec.getAttributeValue(EDITORCONFIG_FILE_ATTR);
      String value = previewRec.getAttributeValue(PREVIEW_FILE_ATTR);
      if (key != null && value != null) {
        myPreviewMap.put(key, value);
      }
    }
  }

  public static EditorConfigPreviewManager getInstance(@NotNull Project project) {
    return project.getService(EditorConfigPreviewManager.class);
  }

  public void associateWithPreviewFile(@NotNull VirtualFile editorConfigFile, @Nullable VirtualFile previewFile) {
    if (previewFile != null) {
      myPreviewMap.put(editorConfigFile.getPath(), previewFile.getPath());
    }
    else {
      myPreviewMap.remove(editorConfigFile.getPath());
    }
  }

  public @Nullable VirtualFile getAssociatedPreviewFile(@NotNull VirtualFile editorConfigFile) {
    final String editorConfigFilePath = editorConfigFile.getPath();
    String previewPathStr = myPreviewMap.get(editorConfigFilePath);
    if (previewPathStr != null) {
      Path previewPath = Paths.get(previewPathStr);
      VirtualFile actualFile = VfsUtil.findFile(previewPath, true);
      if (actualFile == null) {
        myPreviewMap.remove(editorConfigFilePath);
      }
      return actualFile;
    }
    return null;
  }

}
