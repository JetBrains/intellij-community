// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.FileEncodingProvider;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigEncodingManager implements FileEncodingProvider {
  // Handles the following EditorConfig settings:
  public static final String charsetKey = "charset";

  public static final String UTF8_BOM_ENCODING = "utf-8-bom";

  public static final Map<String, Charset> encodingMap;

  static {
    Map<String, Charset> map = new HashMap<>();
    map.put("latin1", StandardCharsets.ISO_8859_1);
    map.put("utf-8", StandardCharsets.UTF_8);
    map.put(UTF8_BOM_ENCODING, StandardCharsets.UTF_8);
    map.put("utf-16be", StandardCharsets.UTF_16BE);
    map.put("utf-16le", StandardCharsets.UTF_16LE);
    encodingMap = Collections.unmodifiableMap(map);
  }

  private final ThreadLocal<Boolean> isApplyingSettings = new ThreadLocal<>();

  @Override
  public @Nullable Charset getEncoding(@NotNull VirtualFile virtualFile) {
    Project project = ProjectLocator.getInstance().guessProjectForFile(virtualFile);
    if (project != null && !Utils.isEnabled(CodeStyle.getSettings(project)) || isIndexing(project) ||
        isApplyingSettings.get() != null && isApplyingSettings.get()) return null;
    try {
      isApplyingSettings.set(true);
      final List<OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(project, virtualFile);
      final String charset = Utils.configValueForKey(outPairs, charsetKey);
      if (!charset.isEmpty()) {
        final Charset newCharset = encodingMap.get(charset);
        if (newCharset != null) {
          return newCharset;
        }
      }
    }
    finally {
      isApplyingSettings.set(false);
    }
    return null;
  }

  private static boolean isIndexing(@Nullable Project project) {
    return project != null
           && !LightEdit.owns(project)
           && DumbService.isDumb(project)
           && "Indexing".equals(Thread.currentThread().getName());
  }
}
