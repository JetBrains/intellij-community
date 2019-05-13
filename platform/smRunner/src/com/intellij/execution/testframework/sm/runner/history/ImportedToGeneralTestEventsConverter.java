/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner.history;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.function.Supplier;

public class ImportedToGeneralTestEventsConverter extends OutputToGeneralTestEventsConverter {

  @NotNull private final TestConsoleProperties myConsoleProperties;
  @NotNull private final File myFile;
  @NotNull private final ProcessHandler myHandler;

  public ImportedToGeneralTestEventsConverter(@NotNull String testFrameworkName,
                                              @NotNull TestConsoleProperties consoleProperties,
                                              @NotNull File file, 
                                              @NotNull ProcessHandler handler) {
    super(testFrameworkName, consoleProperties);
    myConsoleProperties = consoleProperties;
    myFile = file;
    myHandler = handler;
  }

  @Override
  public void onStartTesting() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      parseTestResults();
      myHandler.detachProcess();
    });
  }

  private void parseTestResults() {
    try {
      parseTestResults(() -> {
        try {
          return new InputStreamReader(new FileInputStream(myFile), CharsetToolkit.UTF8_CHARSET);
        }
        catch (FileNotFoundException e) {
          return null;
        }
      }, getProcessor());
    }
    catch (IOException e) {
      final String message = e.getMessage();
      ApplicationManager.getApplication().invokeLater(
        () -> Messages.showErrorDialog(myConsoleProperties.getProject(), message, "Failed to Parse " + myFile.getName()));
    }
  }

  public static void parseTestResults(Supplier<? extends Reader> readerSupplier, GeneralTestEventsProcessor processor) throws IOException {
    try (Reader reader = readerSupplier.get()) {
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.parse(new InputSource(reader), ImportTestOutputExtension.findHandler(readerSupplier, processor));
    }
    catch (ParserConfigurationException | SAXException e) {
      throw new IOException(e);
    }
  }
}
