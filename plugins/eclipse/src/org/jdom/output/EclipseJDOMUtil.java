/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.*;

public class EclipseJDOMUtil {
  private EclipseJDOMUtil() {
  }

  private static EclipseXMLOutputter createOutputter(String lineSeparator) {
    EclipseXMLOutputter xmlOutputter = new EclipseXMLOutputter(lineSeparator);
    Format format = Format.getCompactFormat().
      setIndent("\t").
      setTextMode(Format.TextMode.NORMALIZE).
      setEncoding(CharsetToolkit.UTF8).
      setOmitEncoding(false).
      setOmitDeclaration(false);
    xmlOutputter.setFormat(format);
    return xmlOutputter;
  }

  public static void output(@NotNull Element element, @NotNull File file, @NotNull Project project) throws IOException {
    Writer writer = new OutputStreamWriter(new FileOutputStream(file), CharsetToolkit.UTF8);
    try {
      output(element, writer, project);
    }
    finally {
      writer.close();
    }
  }

  public static void output(@NotNull Element element, @NotNull Writer writer, @NotNull Project project) throws IOException {
    String lineSeparator = CodeStyleSettingsManager.getSettings(project).getLineSeparator();
    writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    writer.write(lineSeparator);
    createOutputter(lineSeparator).output(element, writer);
    writer.write(lineSeparator);
  }
}