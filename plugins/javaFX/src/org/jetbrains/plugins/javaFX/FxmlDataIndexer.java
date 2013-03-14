/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.javaFX;

import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFXNamespaceProvider;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.*;

/**
* User: anna
* Date: 3/14/13
*/
public class FxmlDataIndexer implements DataIndexer<String, Set<String>, FileContent> {
  private static final SAXParser SAX_PARSER = createParser();

  private static SAXParser createParser() {
    try {
      return SAXParserFactory.newInstance().newSAXParser();
    }
    catch (Exception e) {
      return null;
    }
  }

  @Override
  @NotNull
  public Map<String, Set<String>> map(final FileContent inputData) {
    final Map<String, Set<String>> map = getIds(inputData.getContentAsText().toString(), inputData.getFile().getPath());
    if (map != null) {
      return map;
    }
    return Collections.emptyMap();
  }

  @Nullable
  protected Map<String, Set<String>> getIds(String content, final String path) {
    if (!content.contains(JavaFXNamespaceProvider.JAVAFX_NAMESPACE)) {
      return null;
    }

    final Map<String, Set<String>> map = new HashMap<String, Set<String>>();
    try {
      SAX_PARSER.parse(new InputSource(new StringReader(content)), createParseHandler(path, map));
    }
    catch (Exception e) {
      // Do nothing.
    }

    return map;
  }

  protected DefaultHandler createParseHandler(final String path, final Map<String, Set<String>> map) {
    return new DefaultHandler() {
      public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        final String attributesValue = attributes.getValue(FxmlConstants.FX_ID);
        if (attributesValue != null) {
          Set<String> paths = map.get(attributesValue);
          if (paths == null) {
            paths = new HashSet<String>();
            map.put(attributesValue, paths);
          }
          paths.add(path);
        }
      }
    };
  }
}
