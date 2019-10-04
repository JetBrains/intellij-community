// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.CharUtils;
import org.editorconfig.language.psi.EditorConfigHeader;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@State(
  name = "EditorConfigPreviewManager",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class EditorConfigPreviewManager implements PersistentStateComponent<Element> {

  private final Map<String, String> myPreviewMap = new HashMap<>();

  public static final String PREVIEW_RECORD_TAG     = "editorConfig";
  public static final String EDITORCONFIG_FILE_ATTR = "file";
  public static final String PREVIEW_FILE_ATTR      = "previewFile";

  @Nullable
  @Override
  public Element getState() {
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
    return ServiceManager.getService(project, EditorConfigPreviewManager.class);
  }

  @NotNull
  static List<String> extractExtensions(@NotNull EditorConfigHeader header) {
    List<String> extensions = new ArrayList<>();
    CharSequence headerChars = header.getNode().getChars();
    boolean isInExt = false;
    StringBuilder extBuilder = new StringBuilder();
    for (int i = 0; i < headerChars.length(); i ++) {
      char c = headerChars.charAt(i);
      if (c == '.') {
        isInExt = true;
      }
      else if ((CharUtils.isAsciiAlpha(c) || CharUtils.isAsciiNumeric(c)) && isInExt) {
        extBuilder.append(c);
      }
      else {
        if (isInExt && extBuilder.length() > 0) {
          extensions.add(extBuilder.toString());
          extBuilder = new StringBuilder();
        }
        isInExt = false;
      }
    }
    return extensions;
  }


  public void associateWithPreviewFile(@NotNull VirtualFile editorConfigFile, @Nullable VirtualFile previewFile) {
    if (previewFile != null) {
      myPreviewMap.put(editorConfigFile.getPath(), previewFile.getPath());
    }
    else {
      myPreviewMap.remove(editorConfigFile.getPath());
    }
  }

  @Nullable
  public VirtualFile getAssociatedPreviewFile(@NotNull VirtualFile editorConfigFile) {
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
