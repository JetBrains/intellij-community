// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.indexing.*;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.Collections;
import java.util.Map;

@ApiStatus.Internal
public abstract class PluginXmlIndexBase<K, V> extends FileBasedIndexExtension<K, V> {

  protected static final int BASE_INDEX_VERSION = 11;

  protected abstract Map<K, V> performIndexing(IdeaPlugin plugin);

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE);
  }

  @Override
  public @NotNull DataIndexer<K, V, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      public @NotNull Map<K, V> map(@NotNull FileContent inputData) {
        IdeaPlugin plugin = obtainIdeaPlugin(inputData);
        if (plugin == null) return Collections.emptyMap();

        return performIndexing(plugin);
      }
    };
  }

  private static @Nullable IdeaPlugin obtainIdeaPlugin(@NotNull FileContent content) {
    if (!looksLikeIdeaPluginXml(content)) return null;

    PsiFile file = content.getPsiFile();
    if (!(file instanceof XmlFile)) return null;

    return DescriptorUtil.getIdeaPlugin((XmlFile)file);
  }

  private static boolean looksLikeIdeaPluginXml(@NotNull FileContent content) {
    CharSequence text = content.getContentAsText();
    int idx = 0;

    while (true) {
      if (idx == -1) return false;

      // find open tag
      idx = CharArrayUtil.indexOf(text, "<", idx);
      if (idx == -1) return false;

      // ignore processing & comment tags
      if (CharArrayUtil.regionMatches(text, idx, "<!--")) {
        idx = CharArrayUtil.indexOf(text, "-->", idx);
        continue;
      }

      if (CharArrayUtil.regionMatches(text, idx, "<?")) {
        idx = CharArrayUtil.indexOf(text, "?>", idx);
        continue;
      }

      return CharArrayUtil.regionMatches(text, idx, "<idea-plugin"); //NON-NLS
    }
  }
}
