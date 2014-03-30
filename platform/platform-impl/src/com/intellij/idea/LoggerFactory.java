/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public class LoggerFactory implements Logger.Factory {
  private static final String SYSTEM_MACRO = "$SYSTEM_DIR$";
  private static final String APPLICATION_MACRO = "$APPLICATION_DIR$";
  private static final String LOG_DIR_MACRO = "$LOG_DIR$";

  private boolean myInitialized = false;

  private LoggerFactory() { }

  @Override
  public synchronized Logger getLoggerInstance(String name) {
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

      File logXmlFile = FileUtil.findFirstThatExist(PathManager.getHomePath() + "/bin/log.xml",
                                                    PathManager.getHomePath() + "/community/bin/log.xml");
      if (logXmlFile == null) {
        throw new RuntimeException("log.xml file does not exist! Path: [ $home/bin/log.xml]");
      }

      String text = FileUtil.loadFile(logXmlFile);
      text = StringUtil.replace(text, SYSTEM_MACRO, StringUtil.replace(PathManager.getSystemPath(), "\\", "\\\\"));
      text = StringUtil.replace(text, APPLICATION_MACRO, StringUtil.replace(PathManager.getHomePath(), "\\", "\\\\"));
      text = StringUtil.replace(text, LOG_DIR_MACRO, StringUtil.replace(PathManager.getLogPath(), "\\", "\\\\"));

      File file = new File(PathManager.getLogPath());
      if (!file.mkdirs() && !file.exists()) {
        System.err.println("Cannot create log directory: " + file);
      }

      new DOMConfigurator().doConfigure(new StringReader(text), LogManager.getLoggerRepository());

      myInitialized = true;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
