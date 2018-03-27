/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.ant;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class AntImportsIndex extends ScalarIndexExtension<Integer>{
  public static final ID<Integer, Void> INDEX_NAME = ID.create("ant-imports");
  private static final int VERSION = 5;
  public static final Integer ANT_FILES_WITH_IMPORTS_KEY = new Integer(0);
  
  private static final DataIndexer<Integer,Void,FileContent> DATA_INDEXER = new DataIndexer<Integer, Void, FileContent>() {
    @Override
    @NotNull
    public Map<Integer, Void> map(@NotNull final FileContent inputData) {
      final Map<Integer, Void> map = new HashMap<>();

      NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new NanoXmlUtil.IXMLBuilderAdapter() {
        private boolean isFirstElement = true;
        @Override
        public void startElement(final String elemName, final String nsPrefix, final String nsURI, final String systemID, final int lineNr) throws Exception {
          if (isFirstElement) {
            if (!"project".equalsIgnoreCase(elemName)) {
              stop();
            }
            isFirstElement = false;
          }
          else {
            if ("import".equalsIgnoreCase(elemName) || "include".equalsIgnoreCase(elemName)) {
              map.put(ANT_FILES_WITH_IMPORTS_KEY, null);
              stop();
            }
          }
        }

        @Override
        public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type) throws Exception {
          //if (myAttributes != null) {
          //  myAttributes.add(key);
          //}
        }

        @Override
        public void elementAttributesProcessed(final String name, final String nsPrefix, final String nsURI) throws Exception {
          //if (myAttributes != null) {
          //  if (!(myAttributes.contains("name") && myAttributes.contains("default"))) {
          //    stop();
          //  }
          //  myAttributes = null;
          //}
        }

      });
      return map;
    }
  };

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  @NotNull
  public ID<Integer, Void> getName() {
    return INDEX_NAME;
  }

  @Override
  @NotNull
  public DataIndexer<Integer, Void, FileContent> getIndexer() {
    return DATA_INDEXER;
  }

  @NotNull
  @Override
  public KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(StdFileTypes.XML);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }
}
