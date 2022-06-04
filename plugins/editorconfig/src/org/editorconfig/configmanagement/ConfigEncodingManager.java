// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.FileEncodingProvider;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ConfigEncodingManager implements FileEncodingProvider {
  // Handles the following EditorConfig settings:
  public static final String charsetKey = "charset";

  public static final String UTF8_BOM_ENCODING = "utf-8-bom";
  public static final String UTF8_ENCODING     = "utf-8";

  // @formatter:off
  private static final Map<String, Charset> encodingMap =
      Map.of(
        "latin1",          StandardCharsets.ISO_8859_1,
        UTF8_ENCODING,     StandardCharsets.UTF_8,
        UTF8_BOM_ENCODING, StandardCharsets.UTF_8,
        "utf-16be",        StandardCharsets.UTF_16BE,
        "utf-16le",        StandardCharsets.UTF_16LE);
  // @formatter:on

  @Override
  public @Nullable Charset getEncoding(@NotNull VirtualFile virtualFile) {
    Project project = ProjectLocator.getInstance().guessProjectForFile(virtualFile);
    return
      project != null && isEnabledFor(project, virtualFile) ?
      EditorConfigEncodingCache.getInstance().getCachedEncoding(virtualFile) : null;
  }

  @Nullable
  public static String toString(@NotNull Charset charset, boolean useBom) {
    if (charset == StandardCharsets.UTF_8) {
      return useBom ? UTF8_BOM_ENCODING : UTF8_ENCODING;
    }
    return ObjectUtils.doIfNotNull(
      ContainerUtil.find(encodingMap.entrySet(), e -> e.getValue() == charset), entry -> entry.getKey());
  }

  @Nullable
  public static Charset toCharset(@NotNull String str) {
    return encodingMap.get(str);
  }

  static boolean isEnabledFor(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return Utils.isEnabled(CodeStyle.getSettings(project)) &&
           Utils.isApplicableTo(virtualFile) && !Utils.isEditorConfigFile(virtualFile);
  }
}
