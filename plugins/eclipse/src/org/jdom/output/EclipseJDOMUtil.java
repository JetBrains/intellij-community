package org.jdom.output;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jdom.Document;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;

public class EclipseJDOMUtil {

  @NonNls private static final String ENCODING = CharsetToolkit.UTF8;


  private EclipseJDOMUtil() {
  }


  private static EclipseXMLOutputter createOutputter(String lineSeparator) {
    EclipseXMLOutputter xmlOutputter = new EclipseXMLOutputter(lineSeparator);
    Format format = Format.getCompactFormat().
      setIndent("\t").
      setTextMode(Format.TextMode.NORMALIZE).
      setEncoding(ENCODING).
      setOmitEncoding(false).
      setOmitDeclaration(false);
    xmlOutputter.setFormat(format);
    return xmlOutputter;
  }

  public static void output(final Document doc, final File file, final Project project) throws IOException {
    FileOutputStream out = new FileOutputStream(file);
    try {
      createOutputter(CodeStyleSettingsManager.getSettings(project).getLineSeparator()).output(doc, out);
    }
    finally {
      out.close();
    }
  }

  public static void output(final Document document, final Writer writer, Project project) throws IOException {
    createOutputter(CodeStyleSettingsManager.getSettings(project).getLineSeparator()).output(document, writer);
  }
}
