// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.indexing;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.text.CharSequenceReader;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import net.n3.nanoxml.IXMLBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxNamespaceDataProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FxmlDataIndexer implements DataIndexer<String, Void, FileContent> {
  @Override
  public @NotNull Map<String, Void> map(final @NotNull FileContent inputData) {
    final Map<String, Void> map = getIds(inputData.getContentAsText());
    return map != null ? map : Collections.emptyMap();
  }

  private @Nullable Map<String, Void> getIds(@NotNull CharSequence content) {
    if (!StringUtil.contains(content, JavaFxNamespaceDataProvider.JAVAFX_NAMESPACE)) {
      return null;
    }

    final Map<String, Void> map = new HashMap<>();
    final IXMLBuilder handler = createParseHandler(map);
    try {
      NanoXmlUtil.parse(new CharSequenceReader(content), handler);
    }
    catch (StopException ignore) {}
    return map;
  }

  protected IXMLBuilder createParseHandler(@NotNull Map<String, Void> map) {
    return new NanoXmlBuilder() {
      @Override
      public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
        if (value != null && FxmlConstants.FX_ID.equals(nsPrefix + ":" + key)) {
          map.put(value, null);
        }
      }
    };
  }

  protected static final class StopException extends RuntimeException {}
}
