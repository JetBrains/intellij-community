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

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.CommonProcessors;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
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
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

public class JavaFxIdsIndex extends FileBasedIndexExtension<String, Set<String>> {

  @NonNls public static final ID<String, Set<String>> KEY = ID.create("javafx.id.name");

  private final KeyDescriptor<String> myKeyDescriptor = new EnumeratorStringDescriptor();
  private final FileBasedIndex.InputFilter myInputFilter = new JavaFxControllerClassIndex.MyInputFilter();
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();
  private final MyDataExternalizer myDataExternalizer = new MyDataExternalizer();

  @NotNull
  @Override
  public DataIndexer<String, Set<String>, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @Override
  public DataExternalizer<Set<String>> getValueExternalizer() {
    return myDataExternalizer;
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  @NotNull
  @Override
  public ID<String, Set<String>> getName() {
    return KEY;
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @NotNull
  public static Collection<String> getAllRegisteredIds(Project project) {
    CommonProcessors.CollectUniquesProcessor<String> processor = new CommonProcessors.CollectUniquesProcessor<String>();
    FileBasedIndex.getInstance().processAllKeys(KEY, processor, project);
    return processor.getResults();
  }

  @NotNull
  public static Collection<String> getFilePaths(Project project, String id) {
    final List<Set<String>> values = FileBasedIndex.getInstance().getValues(KEY, id, GlobalSearchScope.projectScope(project));
    return (Collection<String>)(values.isEmpty() ? Collections.emptySet() : values.get(0));
  }
  
  private static class MyDataIndexer implements DataIndexer<String, Set<String>, FileContent> {
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
    private static Map<String, Set<String>> getIds(String content, final String path) {
      if (!content.contains(JavaFXNamespaceProvider.JAVAFX_NAMESPACE)) {
        return null;
      }

      final Map<String, Set<String>> map = new HashMap<String, Set<String>>();
      try {
        SAX_PARSER.parse(new InputSource(new StringReader(content)), new DefaultHandler() {
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
        });
      }
      catch (Exception e) {
        // Do nothing.
      }

      return map;
    }
  }

  private static class MyDataExternalizer implements DataExternalizer<Set<String>> {
    @Override
    public void save(DataOutput out, Set<String> value) throws IOException {
      out.writeInt(value.size());
      for (String s : value) {
        out.writeUTF(s);
      }
    }

    @Override
    public Set<String> read(DataInput in) throws IOException {
      final int size = in.readInt();
      final Set<String> result = new HashSet<String>(size);

      for (int i = 0; i < size; i++) {
        final String s = in.readUTF();
        result.add(s);
      }
      return result;
    }
  }
}