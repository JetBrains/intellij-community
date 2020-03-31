// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.Collections;
import java.util.Map;

abstract class PluginXmlIndexBase<K, V> extends FileBasedIndexExtension<K, V> {

  protected abstract Map<K, V> performIndexing(IdeaPlugin plugin);

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(StdFileTypes.XML);
  }

  @NotNull
  @Override
  public DataIndexer<K, V, FileContent> getIndexer() {
    return new DataIndexer<K, V, FileContent>() {
      @NotNull
      @Override
      public Map<K, V> map(@NotNull FileContent inputData) {
        IdeaPlugin plugin = obtainIdeaPlugin(inputData);
        if (plugin == null) return Collections.emptyMap();

        return performIndexing(plugin);
      }
    };
  }

  @Nullable
  private static IdeaPlugin obtainIdeaPlugin(@NotNull FileContent content) {
    PsiFile file = content.getPsiFile();
    if (!(file instanceof XmlFile)) return null;

    return DescriptorUtil.getIdeaPlugin((XmlFile)file);
  }
}
