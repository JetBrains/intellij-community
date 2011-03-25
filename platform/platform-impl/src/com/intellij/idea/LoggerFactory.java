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
package com.intellij.idea;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.log4j.LogManager;
import org.apache.log4j.xml.DOMConfigurator;

import java.io.File;
import java.io.StringReader;

@SuppressWarnings({"HardCodedStringLiteral"})
public class LoggerFactory implements Logger.Factory {
  private static final String SYSTEM_MACRO = "$SYSTEM_DIR$";
  private static final String APPLICATION_MACRO = "$APPLICATION_DIR$";
  private static final String COMMENT_LINE_FOR_TEST_MODE_MACRO = "$COMMENT_LINE_FOR_TEST_MODE$";
  private static final String LOGDIR_MACRO = "$LOG_DIR$";

  private boolean myInitialized = false;

  private static final LoggerFactory ourInstance = new LoggerFactory();
  public static final String LOG_DIR = "log";

  public static LoggerFactory getInstance() {
    return ourInstance;
  }

  private LoggerFactory() {
  }

  public Logger getLoggerInstance(String name) {
    synchronized (this) {
      try {
        if (!isInitialized()) {
          init();
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }

      return new IdeaLogger(org.apache.log4j.Logger.getLogger(name));
    }
  }

  private void init() {
    try {
      /*
      //debug code. Don't delete.
      ClassLoader classLoader = Logger.class.getClassLoader();
      if (!(classLoader.getClass().getName().startsWith("com.intellij"))) {
        System.err.println("Logger shouldn't be used outside the PluginManager");
        Thread.dumpStack();
      }
      */

      System.setProperty("log4j.defaultInitOverride", "true");
      File logXmlFile = FileUtil.findFirstThatExist(PathManager.getHomePath() + "/bin/log.xml", PathManager.getHomePath() + "/community/bin/log.xml");
      if (logXmlFile == null) {
        throw new RuntimeException("log.xml file does not exist! Path: [ $home/bin/log.xml]");
      }

      String text = FileUtil.loadFile(logXmlFile);
      text = StringUtil.replace(text, SYSTEM_MACRO, StringUtil.replace(PathManager.getSystemPath(), "\\", "\\\\"));
      text = StringUtil.replace(text, APPLICATION_MACRO, StringUtil.replace(PathManager.getHomePath(), "\\", "\\\\"));
      text = StringUtil.replace(text, LOGDIR_MACRO, StringUtil.replace(PathManager.getLogPath(), "\\", "\\\\"));

      if ("true".equals(System.getProperty("idea.test.test_mode"))) {
        text = commentTestModeLines(text);
      }

      File file = new File(PathManager.getLogPath());
      file.mkdirs();

      new DOMConfigurator().doConfigure(new StringReader(text), LogManager.getLoggerRepository());

      myInitialized = true;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private boolean isInitialized() {
    return myInitialized;
  }

  private static String commentTestModeLines(String text) {
    String result = text;
    int index = text.indexOf(COMMENT_LINE_FOR_TEST_MODE_MACRO);
    if (index != -1) {
      String str1 = result.substring(0, index);
      String str2 = result.substring(index);
      int firstLineChar = Math.max(str1.lastIndexOf('\n'), str1.lastIndexOf('\r'));
      int lastLineChar = str2.indexOf('\n');
      int lastLineChar2 = str2.indexOf('\r');
      if (lastLineChar == -1) {
        lastLineChar = lastLineChar2;
      }
      else if (lastLineChar2 != -1) {
        lastLineChar = Math.min(lastLineChar, lastLineChar2);
      }
      if (firstLineChar != -1) {
        str1 = str1.substring(0, firstLineChar);
      }
      if (lastLineChar != -1) {
        str2 = str2.substring(lastLineChar);
      }
      result = commentTestModeLines(str1) + commentTestModeLines(str2);
    }
    return result;
  }
}
