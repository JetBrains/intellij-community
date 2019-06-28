// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.diagnostic.DialogAppender;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharSequenceReader;
import org.apache.log4j.*;
import org.apache.log4j.varia.LevelRangeFilter;
import org.apache.log4j.xml.DOMConfigurator;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.adapters.JAXPDOMAdapter;
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

  private static final String DOCUMENT_BUILDER_FACTORY_KEY = "javax.xml.parsers.DocumentBuilderFactory";
  @SuppressWarnings("SpellCheckingInspection")
  private static final String DOCUMENT_BUILDER_FACTORY_IMPL = "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl";

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

  private static void init() throws Exception {
    System.setProperty("log4j.defaultInitOverride", "true");

    File xmlFile = PathManager.getLogFile();
    if (xmlFile != null) {
      loadFromXmlFile(xmlFile);
    }
    else {
      configureProgrammatically();
    }
  }

  private static void loadFromXmlFile(File xmlFile) throws Exception {
    String text = FileUtilRt.loadFile(xmlFile);
    text = StringUtil.replace(text, SYSTEM_MACRO, StringUtil.replace(PathManager.getSystemPath(), "\\", "\\\\"));
    text = StringUtil.replace(text, APPLICATION_MACRO, StringUtil.replace(PathManager.getHomePath(), "\\", "\\\\"));
    text = StringUtil.replace(text, LOG_DIR_MACRO, StringUtil.replace(PathManager.getLogPath(), "\\", "\\\\"));

    File file = new File(PathManager.getLogPath());
    if (!file.mkdirs() && !file.exists()) {
      System.err.println("Cannot create log directory: " + file);
    }

    // Jdom is used instead of XML DOM because of https://youtrack.jetbrains.com/issue/IDEA-173468
    // DOMConfigurator really wants Document
    @SuppressWarnings("deprecation")
    Document document = JDOMUtil.loadDocument(new CharSequenceReader(text));
    Element element = new DOMOutputter(new JAXPDOMAdapter() {
      @Override
      public org.w3c.dom.Document createDocument() throws JDOMException {
        String property = System.setProperty(DOCUMENT_BUILDER_FACTORY_KEY, DOCUMENT_BUILDER_FACTORY_IMPL);
        try {
          return super.createDocument();
        }
        finally {
          if (property == null) {
            System.clearProperty(DOCUMENT_BUILDER_FACTORY_KEY);
          }
          else {
            System.setProperty(DOCUMENT_BUILDER_FACTORY_KEY, property);
          }
        }
      }
    }, null, null).output(document).getDocumentElement();
    new DOMConfigurator().doConfigure(element, LogManager.getLoggerRepository());
  }

  private static void configureProgrammatically() throws IOException {
    org.apache.log4j.Logger root = LogManager.getRootLogger();
    root.removeAllAppenders();
    root.setLevel(Level.INFO);

    PatternLayout layout = new PatternLayout("%d [%7r] %6p - %30.30c - %m \n");

    RollingFileAppender ideaLog = new RollingFileAppender(layout, PathManager.getLogPath() + "/" + "idea.log", true);
    ideaLog.setEncoding("UTF-8");
    ideaLog.setMaxBackupIndex(12);
    ideaLog.setMaximumFileSize(10_000_000);
    root.addAppender(ideaLog);

    ConsoleAppender consoleWarn = new ConsoleAppender(layout, ConsoleAppender.SYSTEM_ERR);
    LevelRangeFilter warnFilter = new LevelRangeFilter();
    warnFilter.setLevelMin(Level.WARN);
    consoleWarn.addFilter(warnFilter);
    root.addAppender(consoleWarn);

    DialogAppender appender = new DialogAppender();
    LevelRangeFilter filter = new LevelRangeFilter();
    filter.setLevelMin(Level.INFO);
    appender.addFilter(filter);

    root.addAppender(appender);
  }
}
