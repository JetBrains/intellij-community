// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.indexing.*;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.impl.AbstractCollectionChildDescription;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

abstract class PluginXmlIndexBase<K, V> extends FileBasedIndexExtension<K, V> {

  protected abstract Map<K, V> performIndexing(IdeaPlugin plugin);

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE);
  }

  @NotNull
  @Override
  public DataIndexer<K, V, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @NotNull
      @Override
      public Map<K, V> map(@NotNull FileContent inputData) {
        IdeaPlugin plugin = obtainIdeaPlugin(inputData);
        if (plugin == null) return Collections.emptyMap();

        return performIndexing(plugin);
      }
    };
  }

  // skip any xi:include
  protected static List<? extends DomElement> getChildrenWithoutIncludes(DomElement parent, @NonNls String tagName) {
    AbstractCollectionChildDescription collectionChildDescription =
      (AbstractCollectionChildDescription)parent.getGenericInfo().getCollectionChildDescription(tagName);
    DomInvocationHandler handler = Objects.requireNonNull(DomManagerImpl.getDomInvocationHandler(parent));
    return handler.getCollectionChildren(collectionChildDescription, false);
  }

  @Nullable
  private static IdeaPlugin obtainIdeaPlugin(@NotNull FileContent content) {
    if (!looksLikeIdeaPluginXml(content)) return null;

    PsiFile file = content.getPsiFile();
    if (!(file instanceof XmlFile)) return null;

    return DescriptorUtil.getIdeaPlugin((XmlFile)file);
  }

  private static boolean looksLikeIdeaPluginXml(@NotNull FileContent content) {
    CharSequence text = content.getContentAsText();
    int idx = 0;

    while (true) {
      // find open tag
      idx = CharArrayUtil.indexOf(text, "<", idx);
      if (idx == -1) return false;

      // ignore processing & comment tags
      if (CharArrayUtil.regionMatches(text, idx, "<!--") ||
          CharArrayUtil.regionMatches(text, idx, "<?")) {
        idx++;
        continue;
      }

      return CharArrayUtil.regionMatches(text, idx, "<idea-plugin"); //NON-NLS
    }
  }
}
