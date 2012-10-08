/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android;

import com.android.AndroidConstants;
import com.android.sdklib.SdkConstants;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidIdIndex extends FileBasedIndexExtension<String, Set<String>> {
  public static final ID<String, Set<String>> INDEX_ID = ID.create("android.id.index");
  public static final String MARKER = "$";

  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    @Override
    public boolean acceptInput(final VirtualFile file) {
      return (file.getFileSystem() == LocalFileSystem.getInstance() || file.getFileSystem() instanceof TempFileSystem) &&
             file.getFileType() == StdFileTypes.XML;
    }
  };

  private static final DataIndexer<String, Set<String>, FileContent> INDEXER = new DataIndexer<String, Set<String>, FileContent>() {
    @Override
    @NotNull
    public Map<String, Set<String>> map(FileContent inputData) {
      final CharSequence content = inputData.getContentAsText();

      if (content == null || CharArrayUtil.indexOf(content, SdkConstants.NS_RESOURCES, 0) == -1) {
        return Collections.emptyMap();
      }
      final HashMap<String, Set<String>> map = new HashMap<String, Set<String>>();
      
      NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new NanoXmlUtil.IXMLBuilderAdapter() {
        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
          super.addAttribute(key, nsPrefix, nsURI, value, type);

          if (AndroidResourceUtil.isIdDeclaration(value)) {
            final String id = AndroidResourceUtil.getResourceNameByReferenceText(value);

            if (id != null) {
              map.put(id, Collections.<String>emptySet());
            }
          }
        }
      });
      if (map.size() > 0) {
        map.put(MARKER, new HashSet<String>(map.keySet()));
      }
      return map;
    }
  };

  private static final DataExternalizer<Set<String>> DATA_EXTERNALIZER = new DataExternalizer<Set<String>>() {
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
  };

  @NotNull
  @Override
  public ID<String, Set<String>> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<String, Set<String>, FileContent> getIndexer() {
    return INDEXER;
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return new EnumeratorStringDescriptor();
  }

  @Override
  public DataExternalizer<Set<String>> getValueExternalizer() {
    return DATA_EXTERNALIZER;
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 3;
  }
}
