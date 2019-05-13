// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jdom.output.DOMOutputter;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.io.File;

@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public class LoggerFactory implements Logger.Factory {
  private static final String SYSTEM_MACRO = "$SYSTEM_DIR$";
  private static final String APPLICATION_MACRO = "$APPLICATION_DIR$";
  private static final String LOG_DIR_MACRO = "$LOG_DIR$";

  private boolean myInitialized = false;

  private LoggerFactory() { }

  @NotNull
  @Override
  public synchronized Logger getLoggerInstance(@NotNull String name) {
    try {
      if (!myInitialized) {
        init();
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return new IdeaLogger(org.apache.log4j.Logger.getLogger(name));
  }

  private void init() {
    try {
      System.setProperty("log4j.defaultInitOverride", "true");

      File logXmlFile = PathManager.getLogFile();

      String text = FileUtilRt.loadFile(logXmlFile);
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

      myInitialized = true;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
