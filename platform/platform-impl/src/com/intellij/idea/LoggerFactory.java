// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.diagnostic.DialogAppender;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import org.apache.log4j.*;
import org.apache.log4j.varia.LevelRangeFilter;
import org.apache.log4j.xml.DOMConfigurator;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.adapters.JAXPDOMAdapter;
import org.jdom.output.DOMOutputter;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LoggerFactory implements Logger.Factory {
  private static final String SYSTEM_MACRO = "$SYSTEM_DIR$";
  private static final String APPLICATION_MACRO = "$APPLICATION_DIR$";
  private static final String LOG_DIR_MACRO = "$LOG_DIR$";

  LoggerFactory() throws Exception {
    System.setProperty("log4j.defaultInitOverride", "true");

    String configPath = System.getProperty(PathManager.PROPERTY_LOG_CONFIG_FILE);
    if (configPath != null) {
      Path configFile = Paths.get(configPath);
      if (!configFile.isAbsolute()) {
        configFile = Paths.get(PathManager.getBinPath()).resolve(configPath);  // look from the 'bin/' directory where log.xml was used to be
      }
      if (Files.exists(configFile)) {
        configureFromXmlFile(configFile);
        return;
      }
    }

    configureProgrammatically();
  }

  @Override
  public @NotNull Logger getLoggerInstance(@NotNull String name) {
    return new IdeaLogger(LogManager.getLoggerRepository().getLogger(name));
  }

  private static void configureFromXmlFile(@NotNull Path xmlFile) throws Exception {
    String text = new String(Files.readAllBytes(xmlFile), StandardCharsets.UTF_8);
    text = text.replace(SYSTEM_MACRO, PathManager.getSystemPath().replace("\\", "\\\\"));
    text = text.replace(APPLICATION_MACRO, PathManager.getHomePath().replace("\\", "\\\\"));
    text = text.replace(LOG_DIR_MACRO, PathManager.getLogPath().replace("\\", "\\\\"));

    // JDOM is used instead of XML DOM because of IDEA-173468 (`DOMConfigurator` really wants `Document`)
    @SuppressWarnings("deprecation") Document document = JDOMUtil.loadDocument(new StringReader(text));
    Element element = new DOMOutputter(new JAXPDOMAdapter() {
      @Override
      public org.w3c.dom.Document createDocument() throws JDOMException {
        String key = "javax.xml.parsers.DocumentBuilderFactory";
        @SuppressWarnings("SpellCheckingInspection") String property = System.setProperty(key, "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
        try {
          return super.createDocument();
        }
        finally {
          if (property == null) {
            System.clearProperty(key);
          }
          else {
            System.setProperty(key, property);
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

    RollingFileAppender ideaLog = new RollingFileAppender(layout, PathManager.getLogPath() + "/idea.log", true);
    ideaLog.setEncoding(StandardCharsets.UTF_8.name());
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