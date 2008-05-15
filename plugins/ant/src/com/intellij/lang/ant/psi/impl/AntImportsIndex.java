package com.intellij.lang.ant.psi.impl;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.xml.NanoXmlUtil;

import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 28, 2008
 */
public class AntImportsIndex extends ScalarIndexExtension<Integer>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.AntImportsIndex");
  public static final ID<Integer, Void> INDEX_NAME = new ID<Integer, Void>("ant-imports");
  private static final int VERSION = 4;
  public static final Integer ANT_FILES_WITH_IMPORTS_KEY = new Integer(0);
  
  private static final DataIndexer<Integer,Void,FileContent> DATA_INDEXER = new DataIndexer<Integer, Void, FileContent>() {
    public Map<Integer, Void> map(final FileContent inputData) {
      final Map<Integer, Void> map = new HashMap<Integer, Void>();
      
      NanoXmlUtil.parse(new StringReader(inputData.getContentAsText().toString()), new NanoXmlUtil.IXMLBuilderAdapter() {
        private boolean isFirstElement = true;
        private Set<String> myAttributes = new HashSet<String>();
        public void startElement(final String elemName, final String nsPrefix, final String nsURI, final String systemID, final int lineNr) throws Exception {
          if (isFirstElement) {
            if (!"project".equalsIgnoreCase(elemName)) {
              stop();
            }
            isFirstElement = false;
          }
          else {
            if ("import".equalsIgnoreCase(elemName)) {
              map.put(ANT_FILES_WITH_IMPORTS_KEY, null);
              stop();
            }
          }
        }

        public void addAttribute(final String key, final String nsPrefix, final String nsURI, final String value, final String type) throws Exception {
          if (myAttributes != null) {
            myAttributes.add(key);
          }
        }

        public void elementAttributesProcessed(final String name, final String nsPrefix, final String nsURI) throws Exception {
          if (myAttributes != null) {
            if (!(myAttributes.contains("name") && myAttributes.contains("default"))) {
              stop();
            }
            myAttributes = null;
          }
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

  public PersistentEnumerator.DataDescriptor<Integer> getKeyDescriptor() {
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
