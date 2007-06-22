package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jetbrains.idea.eclipse.util.JDOM;
import org.jetbrains.idea.eclipse.util.JDOMCompare;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CachedXmlDocumentSet extends CachedFileSet<Document,Document> {

  protected Document load(final VirtualFile vFile) throws IOException, JDOMException {
    InputStream is = null;
    try {
      is = vFile.getInputStream();
      return JDOM.read (is);
    }
    finally {
      if ( is != null ) {
        is.close();
      }
    }
  }

  protected void save(final Document content, OutputStream os ) throws IOException {
    JDOM.write(content,os);
  }

  protected boolean areEqual(Document one, Document two) {
    return null == JDOMCompare.diffDocuments(one, two);
  }

  protected Document toPhysical(final Document content) {
    return content;
  }

  public Document read (final String name) throws IOException, JDOMException {
    return (Document)load(name).clone();
  }

  public void write(Document document, String name) throws IOException {
    update((Document)document.clone(), name);
  }
}