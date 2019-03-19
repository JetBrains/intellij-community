// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharSequenceReader;
import org.apache.log4j.LogManager;
import org.apache.log4j.xml.DOMConfigurator;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.output.DOMOutputter;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;

@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public class LoggerFactory implements Logger.Factory {
  private static final String SYSTEM_MACRO = "$SYSTEM_DIR$";
  private static final String APPLICATION_MACRO = "$APPLICATION_DIR$";
  private static final String LOG_DIR_MACRO = "$LOG_DIR$";

  LoggerFactory() {
    try {
      init();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @NotNull
  @Override
  public Logger getLoggerInstance(@NotNull String name) {
    return new IdeaLogger(org.apache.log4j.Logger.getLogger(name));
  }

  private static void init() throws IOException, JDOMException {
    System.setProperty("log4j.defaultInitOverride", "true");

    String text = FileUtilRt.loadFile(PathManager.getLogFile());
    text = StringUtil.replace(text, SYSTEM_MACRO, StringUtil.replace(PathManager.getSystemPath(), "\\", "\\\\"));
    text = StringUtil.replace(text, APPLICATION_MACRO, StringUtil.replace(PathManager.getHomePath(), "\\", "\\\\"));
    text = StringUtil.replace(text, LOG_DIR_MACRO, StringUtil.replace(PathManager.getLogPath(), "\\", "\\\\"));

    File file = new File(PathManager.getLogPath());
    if (!file.mkdirs() && !file.exists()) {
      System.err.println("Cannot create log directory: " + file);
    }

    // DOMConfigurator really wants Document
    @SuppressWarnings("deprecation")
    Document document = JDOMUtil.loadDocument(new CharSequenceReader(text));
    Element element = new DOMOutputter().output(document).getDocumentElement();
    new DOMConfigurator().doConfigure(element, LogManager.getLoggerRepository());
  }
}
