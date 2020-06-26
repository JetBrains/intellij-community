// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jdom.output;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class EclipseJDOMUtil {
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
    try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
      output(element, writer, project);
    }
  }

  public static void output(@NotNull Element element, @NotNull Writer writer, @NotNull Project project) throws IOException {
    String lineSeparator = CodeStyle.getSettings(project).getLineSeparator();
    writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    writer.write(lineSeparator);
    createOutputter(lineSeparator).output(element, writer);
    writer.write(lineSeparator);
  }
}