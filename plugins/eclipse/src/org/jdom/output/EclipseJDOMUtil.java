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
