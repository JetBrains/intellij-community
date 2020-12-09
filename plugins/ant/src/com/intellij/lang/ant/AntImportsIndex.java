// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.util.xml.NanoXmlBuilder.stop;

/**
 * @author Eugene Zhuravlev
 */
public class AntImportsIndex extends ScalarIndexExtension<Integer>{
  public static final ID<Integer, Void> INDEX_NAME = ID.create("ant-imports");
  private static final int VERSION = 5;
  public static final Integer ANT_FILES_WITH_IMPORTS_KEY = Integer.valueOf(0);

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
    return inputData -> {
      AtomicBoolean importFound = new AtomicBoolean();

      NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new NanoXmlBuilder() {
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
              importFound.set(true);
              stop();
            }
          }
        }
      });

      return importFound.get() ? Collections.singletonMap(ANT_FILES_WITH_IMPORTS_KEY, null) : Collections.emptyMap();
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return file.isInLocalFileSystem();
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }
}
