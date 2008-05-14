package com.intellij.lang.ant.psi.impl;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 28, 2008
 */
public class AntImportsIndex extends ScalarIndexExtension<Integer>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.AntImportsIndex");
  public static final ID<Integer, Void> INDEX_NAME = new ID<Integer, Void>("ant-imports");
  private static final int VERSION = 3;
  public static final Integer ANT_FILES_WITH_IMPORTS_KEY = new Integer(0);
  
  private static final SAXParserFactory ourSAXFactory = SAXParserFactory.newInstance();
  static {
    try {
      ourSAXFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      ourSAXFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      ourSAXFactory.setFeature("http://xml.org/sax/features/resolve-dtd-uris", false);
      try {
        ourSAXFactory.setFeature("http://xml.org/sax/features/string-interning", false);
      }
      catch (SAXNotSupportedException ignored) {
      }
      ourSAXFactory.setFeature("http://xml.org/sax/features/validation", false);
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  private static final DataIndexer<Integer,Void,FileContent> DATA_INDEXER = new DataIndexer<Integer, Void, FileContent>() {
    public Map<Integer, Void> map(final FileContent inputData) {
      final Map<Integer, Void> map = new HashMap<Integer, Void>();
      try {
        final SAXParser parser = ourSAXFactory.newSAXParser();
        parser.parse(new InputSource(new StringReader(inputData.getContentAsText().toString())), new DefaultHandler() {
          private boolean isFirstElement = true;
          public void startElement(String uri, String localName, String elemName, Attributes attributes) throws SAXException {
            if (isFirstElement) {
              isFirstElement = false;
              if ("project".equalsIgnoreCase(elemName)) {
                final String name = attributes.getValue("", "name");
                if (name == null) {
                  throw new SAXException("stop parsing");
                }
                final String defaultTarget = attributes.getValue("", "default");
                if (defaultTarget == null) {
                  throw new SAXException("stop parsing");
                }
              }
              else {
                throw new SAXException("stop parsing");
              }
            }
            else {
              if ("import".equalsIgnoreCase(elemName)) {
                map.put(ANT_FILES_WITH_IMPORTS_KEY, null); 
                throw new SAXException("stop parsing");
              }
            }
          }
        });
      }
      catch (SAXException ignored) {
        //LOG.info(ignored);
      }
      catch (IOException ignored) {
        LOG.info(ignored);
      }
      catch (ParserConfigurationException ignored) {
        LOG.info(ignored);
      }
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
