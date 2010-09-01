/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.psi.impl;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 28, 2008
 */
public class AntImportsIndex extends ScalarIndexExtension<Integer>{
  public static final ID<Integer, Void> INDEX_NAME = ID.create("ant-imports");
  private static final int VERSION = 5;
  public static final Integer ANT_FILES_WITH_IMPORTS_KEY = new Integer(0);
  
  private static final DataIndexer<Integer,Void,FileContent> DATA_INDEXER = new DataIndexer<Integer, Void, FileContent>() {
    @NotNull
    public Map<Integer, Void> map(final FileContent inputData) {
      final Map<Integer, Void> map = new HashMap<Integer, Void>();
      
      NanoXmlUtil.parse(new StringReader(inputData.getContentAsText().toString()), new NanoXmlUtil.IXMLBuilderAdapter() {
        private boolean isFirstElement = true;
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

        public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type) throws Exception {
          //if (myAttributes != null) {
          //  myAttributes.add(key);
          //}
        }

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

  public int getVersion() {
    return VERSION;
  }

  public ID<Integer, Void> getName() {
    return INDEX_NAME;
  }

  public DataIndexer<Integer, Void, FileContent> getIndexer() {
    return DATA_INDEXER;
  }

  public KeyDescriptor<Integer> getKeyDescriptor() {
    return new EnumeratorIntegerDescriptor();
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    final FileTypeManager ftManager = FileTypeManager.getInstance();
    return new FileBasedIndex.InputFilter() {
      public boolean acceptInput(final VirtualFile file) {
        return ftManager.getFileTypeByFile(file) instanceof XmlFileType;
      }
    };
  }

  public boolean dependsOnFileContent() {
    return true;
  }
}
