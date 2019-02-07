// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import net.n3.nanoxml.IXMLBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxNamespaceDataProvider;

import java.io.StringReader;
import java.util.*;

public class FxmlDataIndexer implements DataIndexer<String, Set<String>, FileContent> {
  @Override
  @NotNull
  public Map<String, Set<String>> map(@NotNull final FileContent inputData) {
    final Map<String, Set<String>> map = getIds(inputData.getContentAsText().toString(), inputData.getFile(), inputData.getProject());
    if (map != null) {
      return map;
    }
    return Collections.emptyMap();
  }

  @Nullable
  protected Map<String, Set<String>> getIds(String content, final VirtualFile file, Project project) {
    if (!content.contains(JavaFxNamespaceDataProvider.JAVAFX_NAMESPACE)) {
      return null;
    }

    final Map<String, Set<String>> map = new HashMap<>();
    final String path = file.getPath();
    final IXMLBuilder handler = createParseHandler(path, map);
    try {
      NanoXmlUtil.parse(new StringReader(content), handler);
    }
    catch (StopException ignore) {}
    final VirtualFile sourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(file);
    endDocument(path, sourceRoot, map, handler);
    return map;
  }

  protected void endDocument(String math, VirtualFile sourceRoot, Map<String, Set<String>> map, IXMLBuilder handler){}

  protected IXMLBuilder createParseHandler(final String path, final Map<String, Set<String>> map) {
    return new NanoXmlBuilder() {
      @Override
      public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
        if (value != null && FxmlConstants.FX_ID.equals(nsPrefix + ":" + key)) {
          Set<String> paths = map.get(value);
          if (paths == null) {
            paths = new HashSet<>();
            map.put(value, paths);
          }
          paths.add(path);
        }
      }
    };
  }

  protected static class StopException extends RuntimeException {}
}
