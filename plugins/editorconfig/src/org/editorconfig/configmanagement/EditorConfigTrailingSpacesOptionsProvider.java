// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.fileEditor.TrailingSpacesOptionsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("Duplicates")
public class EditorConfigTrailingSpacesOptionsProvider implements TrailingSpacesOptionsProvider, StandardEditorConfigProperties {

  @Nullable
  @Override
  public TrailingSpacesOptionsProvider.Options getOptions(@NotNull Project project, @NotNull VirtualFile file) {
    if (Utils.isEnabled(CodeStyle.getSettings(project))) {
      final List<EditorConfig.OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(project, file);
      final Boolean trimTrailingWhitespace = getBooleanValue(outPairs, TRIM_TRAILING_WHITESPACE);
      final Boolean insertFinalNewline = getBooleanValue(outPairs, INSERT_FINAL_NEWLINE);
      if (trimTrailingWhitespace != null || insertFinalNewline != null) {
        return new FileOptions(trimTrailingWhitespace, insertFinalNewline);
      }
    }
    return null;
  }


  @Nullable
  private static Boolean getBooleanValue(@NotNull List<EditorConfig.OutPair> pairs, @NotNull String key) {
    final String rawValue = Utils.configValueForKey(pairs, key);
    if ("false".equalsIgnoreCase(rawValue)) {
      return false;
    }
    else if ("true".equalsIgnoreCase(rawValue)) {
      return true;
    }
    return null;
  }

  private static final class FileOptions implements TrailingSpacesOptionsProvider.Options {
    private final @Nullable Boolean myTrimTrailingSpaces;
    private final @Nullable Boolean myInsertFinalNewLine;

    private FileOptions(@Nullable Boolean spaces, @Nullable Boolean line) {
      myTrimTrailingSpaces = spaces;
      myInsertFinalNewLine = line;
    }

    @Nullable
    @Override
    public Boolean getStripTrailingSpaces() {
      return myTrimTrailingSpaces;
    }

    @Nullable
    @Override
    public Boolean getEnsureNewLineAtEOF() {
      return myInsertFinalNewLine;
    }

    @Nullable
    @Override
    public Boolean getChangedLinesOnly() {
      return myTrimTrailingSpaces != null ? !myTrimTrailingSpaces : null;
    }

    @Nullable
    @Override
    public Boolean getKeepTrailingSpacesOnCaretLine() {
      return null;
    }
  }
}
