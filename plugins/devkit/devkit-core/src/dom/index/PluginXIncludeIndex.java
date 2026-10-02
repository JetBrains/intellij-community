// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PathUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.DomUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.inspections.DescriptorTopologyKt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code xi:include} tags of plugin descriptors, keyed by the file name of the href. Each value keeps the raw href
 * and whether the include merges the whole target, so includer lookups resolve hrefs without loading the includer tree.
 * <p>
 * Indexing only {@code <idea-plugin>} files loses no edges: the runtime consumes included content as descriptor
 * elements, so every legal includer in the descriptor graph is itself an {@code <idea-plugin>} file.
 */
@ApiStatus.Internal
public final class PluginXIncludeIndex extends PluginXmlIndexBase<String, List<PluginXIncludeIndex.XIncludeEntry>> {
  static final ID<String, List<XIncludeEntry>> NAME = ID.create("devkit.PluginXIncludeIndex");

  public record XIncludeEntry(@NotNull String href, boolean mergesWholeDescriptor) { }

  private static final DataExternalizer<List<XIncludeEntry>> VALUE_EXTERNALIZER = new DataExternalizer<>() {
    @Override
    public void save(@NotNull DataOutput out, List<XIncludeEntry> value) throws IOException {
      DataInputOutputUtil.writeINT(out, value.size());
      for (XIncludeEntry entry : value) {
        IOUtil.writeUTF(out, entry.href());
        out.writeBoolean(entry.mergesWholeDescriptor());
      }
    }

    @Override
    public List<XIncludeEntry> read(@NotNull DataInput in) throws IOException {
      int size = DataInputOutputUtil.readINT(in);
      List<XIncludeEntry> result = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        result.add(new XIncludeEntry(IOUtil.readUTF(in), in.readBoolean()));
      }
      return result;
    }
  };

  @Override
  public @NotNull ID<String, List<XIncludeEntry>> getName() {
    return NAME;
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull DataExternalizer<List<XIncludeEntry>> getValueExternalizer() {
    return VALUE_EXTERNALIZER;
  }

  @Override
  public int getVersion() {
    return BASE_INDEX_VERSION;
  }

  @Override
  protected Map<String, List<XIncludeEntry>> performIndexing(IdeaPlugin plugin) {
    XmlTag rootTag = DomUtil.getFile(plugin).getRootTag();
    if (rootTag == null) return Collections.emptyMap();
    Map<String, List<XIncludeEntry>> result = new HashMap<>();
    collectXIncludes(rootTag, rootTag, result);
    return result;
  }

  private static void collectXIncludes(XmlTag tag, XmlTag rootTag, Map<String, List<XIncludeEntry>> result) {
    if (isXIncludeTag(tag)) {
      String href = tag.getAttributeValue("href");
      if (!StringUtil.isEmptyOrSpaces(href)) {
        boolean mergesWhole = DescriptorTopologyKt.mergesWholeDescriptor(tag.getParentTag() == rootTag, tag.getAttributeValue("xpointer"));
        result.computeIfAbsent(PathUtil.getFileName(href), _ -> new ArrayList<>(1)).add(new XIncludeEntry(href, mergesWhole));
      }
    }
    // physical children: getSubTags substitutes the included content for every resolvable xi:include tag
    for (PsiElement child : tag.getChildren()) {
      if (child instanceof XmlTag) {
        collectXIncludes((XmlTag)child, rootTag, result);
      }
    }
  }

  // index PSI may not resolve the xi: prefix, so match the prefix too; lookups resolve every href
  static boolean isXIncludeTag(XmlTag tag) {
    return "include".equals(tag.getLocalName()) &&
           (XmlUtil.XINCLUDE_URI.equals(tag.getNamespace()) || "xi".equals(tag.getNamespacePrefix()));
  }

  /**
   * Files in {@code scope} with an {@code xi:include} whose href file name is {@code targetFileName}, with those includes.
   * The key is the file name only; callers resolve the hrefs to tell apart same-named descriptors.
   */
  public static @NotNull Map<VirtualFile, List<XIncludeEntry>> getIncludes(@NotNull String targetFileName, @NotNull GlobalSearchScope scope) {
    Map<VirtualFile, List<XIncludeEntry>> result = new HashMap<>();
    FileBasedIndex.getInstance().processValues(NAME, targetFileName, null, (file, entries) -> {
      result.put(file, entries);
      return true;
    }, scope);
    return result;
  }
}
