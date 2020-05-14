// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigEncodingManager implements FileDocumentManagerListener {
  // Handles the following EditorConfig settings:
  public static final String charsetKey = "charset";

  public static final String UTF8_BOM_ENCODING = "utf-8-bom";

  public static final Map<String, Charset> encodingMap;

  static {
    Map<String, Charset> map = new HashMap<>();
    map.put("latin1", StandardCharsets.ISO_8859_1);
    map.put("utf-8", StandardCharsets.UTF_8);
    map.put(UTF8_BOM_ENCODING, StandardCharsets.UTF_8);
    map.put("utf-16be", CharsetToolkit.UTF_16BE_CHARSET);
    map.put("utf-16le", CharsetToolkit.UTF_16LE_CHARSET);
    encodingMap = Collections.unmodifiableMap(map);
  }

  private boolean isApplyingSettings;

  public ConfigEncodingManager() {
    isApplyingSettings = false;
  }

  @Override
  public void beforeDocumentSaving(@NotNull Document document) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (!isApplyingSettings) {
      Project project = ProjectLocator.getInstance().guessProjectForFile(file);
      if (project != null) {
        applySettings(project, file);
      }
    }
  }

  private void applySettings(Project project, VirtualFile file) {
    if (file == null) return;
    if (!Utils.isEnabled(CodeStyle.getSettings(project))) return;

    // Prevent "setEncoding" calling "saveAll" from causing an endless loop
    isApplyingSettings = true;
    try {
      final List<OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(project, file);
      final EncodingProjectManager encodingProjectManager = EncodingProjectManager.getInstance(project);
      final String charset = Utils.configValueForKey(outPairs, charsetKey);
      if (!charset.isEmpty()) {
        final Charset newCharset = encodingMap.get(charset);
        if (newCharset != null) {
          if (Comparing.equal(newCharset, file.getCharset())) return;
          encodingProjectManager.setEncoding(file, newCharset);
        } else {
          Utils.invalidConfigMessage(project, charset, charsetKey, file.getCanonicalPath());
        }
      }
    } finally {
      isApplyingSettings = false;
    }
  }
}
