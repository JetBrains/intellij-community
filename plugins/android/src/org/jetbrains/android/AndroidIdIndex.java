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

import com.android.SdkConstants;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.util.containers.HashMap;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidIdIndex extends ScalarIndexExtension<String> {
  public static final String[] RES_TYPES_CONTAINING_ID_DECLARATIONS = {SdkConstants.FD_RES_LAYOUT, SdkConstants.FD_RES_MENU};
  public static final ID<String, Void> INDEX_ID = ID.create("android.id.index");
  public static final String MARKER = "$";

  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    @Override
    public boolean acceptInput(final VirtualFile file) {
      return (file.getFileSystem() == LocalFileSystem.getInstance() || file.getFileSystem() instanceof TempFileSystem) &&
             file.getFileType() == StdFileTypes.XML;
    }
  };

  private static final DataIndexer<String, Void, FileContent> INDEXER = new DataIndexer<String, Void, FileContent>() {
    @Override
    @NotNull
    public Map<String, Void> map(FileContent inputData) {
      final CharSequence content = inputData.getContentAsText();

      if (content == null || CharArrayUtil.indexOf(content, SdkConstants.NS_RESOURCES, 0) == -1) {
        return Collections.emptyMap();
      }
      final HashMap<String, Void> ids = new HashMap<String, Void>();
      
      NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new NanoXmlUtil.IXMLBuilderAdapter() {
        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
          super.addAttribute(key, nsPrefix, nsURI, value, type);

          if (AndroidResourceUtil.isIdDeclaration(value)) {
            String id = AndroidResourceUtil.getResourceNameByReferenceText(value);
            if (id != null) {
              if (ids.isEmpty()) {
                ids.put(MARKER, null);
              }
              ids.put(id, null);
            }
          }
        }
      });
      return ids;
    }
  };

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return INDEXER;
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return new EnumeratorStringDescriptor();
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
    return 2;
  }
}
