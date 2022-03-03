// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.history;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.SmRunnerBundle;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
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
          return new InputStreamReader(new FileInputStream(myFile), StandardCharsets.UTF_8);
        }
        catch (FileNotFoundException e) {
          return null;
        }
      }, getProcessor());
    }
    catch (IOException e) {
      final String message = e.getMessage();
      ApplicationManager.getApplication().invokeLater(
        () -> Messages.showErrorDialog(myConsoleProperties.getProject(), message, SmRunnerBundle.message("sm.test.runner.imported.to.general.failed.to.parse.error.title", myFile.getName())));
    }
  }

  public static void parseTestResults(Supplier<? extends Reader> readerSupplier, GeneralTestEventsProcessor processor) throws IOException {
    try (Reader reader = readerSupplier.get()) {
      SAXParser parser = SAXParserFactory.newDefaultInstance().newSAXParser();
      parser.parse(new InputSource(reader), ImportTestOutputExtension.findHandler(readerSupplier, processor));
    }
    catch (ParserConfigurationException | SAXException e) {
      throw new IOException(e);
    }
  }
}
