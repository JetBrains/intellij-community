package org.jdom.output;

import com.intellij.openapi.vfs.CharsetToolkit;
import org.jdom.Document;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;

public class EclipseJDOMUtil {

  @NonNls private static final String ENCODING = CharsetToolkit.UTF8;

  private static final String lineSeparator = "\n";

  private EclipseJDOMUtil() {
  }


  private static EclipseXMLOutputter createOutputter() {
    EclipseXMLOutputter xmlOutputter = new EclipseXMLOutputter();
    Format format = Format.getCompactFormat().
      setIndent("\t").
      setTextMode(Format.TextMode.NORMALIZE).
      setEncoding(ENCODING).
      setOmitEncoding(false).
      setOmitDeclaration(false).
      setLineSeparator(lineSeparator);
    xmlOutputter.setFormat(format);
    return xmlOutputter;
  }

  public static void output(final Document doc, final File file) throws IOException {
    FileOutputStream out = new FileOutputStream(file);
    try {
      createOutputter().output(doc, out);
    }
    finally {
      out.close();
    }
  }

  public static void output(final Document document, final Writer writer) throws IOException {
    createOutputter().output(document, writer);
  }
}
