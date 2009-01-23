package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.output.EclipseJDOMUtil;
import org.jetbrains.idea.eclipse.util.JDOMCompare;

import java.io.*;

public class CachedXmlDocumentSet extends CachedFileSet<Document,Document> {

  protected Document load(final VirtualFile vFile) throws IOException, JDOMException {
    final InputStream is = vFile.getInputStream();
    try {
      return JDOMUtil.loadDocument(is);
    }
    finally {
      is.close();
    }
  }

  protected void save(final Document content, OutputStream os ) throws IOException {
    Writer writer = new OutputStreamWriter(os, "UTF-8");
    try {
      EclipseJDOMUtil.output(content, writer);
    }
    finally {
      writer.close();
    }
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