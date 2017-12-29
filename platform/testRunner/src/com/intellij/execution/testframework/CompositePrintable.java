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
package com.intellij.execution.testframework;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.execution.testframework.ui.TestsOutputConsolePrinter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class CompositePrintable extends UserDataHolderBase implements Printable, Disposable {
  public static final String NEW_LINE = "\n";

  protected final List<Printable> myNestedPrintables = new ArrayList<>();
  private final PrintablesWrapper myWrapper = new PrintablesWrapper();
  protected int myExceptionMark;
  private int myCurrentSize = 0;
  private String myOutputFile = null;
  private String myFrameworkOutputFile;
  private static final ExecutorService ourTestExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("tests", 1);

  public void flush() {
    synchronized (myNestedPrintables) {
      myWrapper.flush(myNestedPrintables);
      clear();
    }
  }

  public void flushOutputFile() {
    synchronized (myNestedPrintables) {
      ArrayList<Printable> printables = new ArrayList<>(myNestedPrintables);
      invokeInAlarm(() -> printOutputFile(printables));
    }
  }

  public static void invokeInAlarm(Runnable runnable) {
    invokeInAlarm(runnable, !ApplicationManager.getApplication().isDispatchThread() ||
                            ApplicationManager.getApplication().isUnitTestMode());
  }

  public static void invokeInAlarm(Runnable runnable, final boolean sync) {
    if (sync) {
      runnable.run();
    } else {
      ourTestExecutorService.execute(runnable);
    }
  }

  public void printOn(final Printer printer) {
    final ArrayList<Printable> printables;
    synchronized (myNestedPrintables) {
      printables = new ArrayList<>(myNestedPrintables);
    }
    myWrapper.printOn(printer, printables);
  }

  public void printOwnPrintablesOn(final Printer printer) {
    printOwnPrintablesOn(printer, true);
  }

  public void printOwnPrintablesOn(@NotNull Printer printer, boolean skipFileContent) {
    List<Printable> printables;
    synchronized (myNestedPrintables) {
      printables = ContainerUtil.filter(myNestedPrintables, printable -> !(printable instanceof AbstractTestProxy));
    }
    myWrapper.printOn(printer, printables, skipFileContent);
  }

  public void addLast(@NotNull final Printable printable) {
    synchronized (myNestedPrintables) {
      myNestedPrintables.add(printable);
      if (myNestedPrintables.size() > 500) {
        flush();
      }
    }
  }

  public void insert(@NotNull final Printable printable, int i) {
    synchronized (myNestedPrintables) {
      if (i >= myNestedPrintables.size()) {
        myNestedPrintables.add(printable);
      } else {
        myNestedPrintables.add(i, printable);
      }
      if (myNestedPrintables.size() > 500) {
        flush();
      }
    }
  }

  protected void clear() {
    synchronized (myNestedPrintables) {
      myCurrentSize += myNestedPrintables.size();
      myNestedPrintables.clear();
    }
  }

  public int getCurrentSize() {
    synchronized (myNestedPrintables) {
      return myCurrentSize + myNestedPrintables.size();
    }
  }

  @Override
  public void dispose() {
    clear();
    myWrapper.dispose();
  }

  public int getExceptionMark() {
    return myExceptionMark;
  }

  public void setExceptionMark(int exceptionMark) {
    myExceptionMark = exceptionMark;
  }

  public void setOutputFilePath(String outputFile) {
    myOutputFile = outputFile;
  }

  public void setFrameworkOutputFile(String frameworkOutputFile) {
    myFrameworkOutputFile = frameworkOutputFile;
  }

  public void printFromFrameworkOutputFile(final Printer console) {
    if (myFrameworkOutputFile != null) {
      final Runnable runnable = () -> {
        final File inputFile = new File(myFrameworkOutputFile);
        if (inputFile.exists()) {
          try {
            final String fileText = FileUtil.loadFile(inputFile);
            console.print(fileText, ConsoleViewContentType.NORMAL_OUTPUT);
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      };
      invokeInAlarm(runnable);
    }
  }

  private static final Logger LOG = Logger.getInstance(PrintablesWrapper.class);

  private class PrintablesWrapper {

    private static final String HYPERLINK = "hyperlink";

    private File myFile;
    private final MyFlushToFilePrinter myPrinter = new MyFlushToFilePrinter();
    private final Object myFileLock = new Object();

    @Nullable
    private synchronized File getFile() {
      if (myFile == null) {
        try {
          final File tempFile = FileUtil.createTempFile("idea_test_", ".out");
          if (tempFile.exists()) {
            myFile = tempFile;
            return myFile;
          }
        }
        catch (IOException e) {
          LOG.error(e);
          return null;
        }
      }
      return myFile;
    }

    public synchronized void dispose() {
      if (myFile != null) FileUtil.delete(myFile);
    }

    public synchronized boolean hasOutput() {
      return myFile != null;
    }

    public void flush(final List<Printable> printables) {
      if (printables.isEmpty()) return;
      final ArrayList<Printable> currentPrintables = new ArrayList<>(printables);
      //move out from AWT thread
      final Runnable request = () -> {
        synchronized (myFileLock) {
          for (final Printable printable : currentPrintables) {
            printable.printOn(myPrinter);
          }
          myPrinter.close();
        }
        printOutputFile(currentPrintables);
      };
      invokeInAlarm(request, ApplicationManager.getApplication().isUnitTestMode());
    }

    public void printOn(final Printer console, final List<Printable> printables) {
      printOn(console, printables, false);
    }

    public void printOn(final Printer console, final List<Printable> printables, final boolean skipFileContent) {
      final Runnable request = () -> {
        if (skipFileContent) {
          readFileContentAndPrint(console, null, printables);
          return;
        }
        final File file = hasOutput() ? getFile() : null;
        synchronized (myFileLock) {
          readFileContentAndPrint(console, file, printables);
        }
      };
      invokeInAlarm(request);
    }

    private class MyFlushToFilePrinter implements Printer {
      //all access is performed from alarm thread
      private DataOutputStream myFileWriter;

      private DataOutputStream getFileWriter() {
        if (myFileWriter == null) {
          try {
            final File file = getFile();
            LOG.assertTrue(file != null);
            myFileWriter = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file, true)));
          }
          catch (FileNotFoundException e) {
            LOG.info(e);
            return null;
          }
        }
        return myFileWriter;
      }

      private void close() {
        if (myFileWriter != null) {
          try {
            myFileWriter.close();
          }
          catch (FileNotFoundException e) {
            LOG.info(e);
          }
          catch (IOException e) {
            LOG.error(e);
          }
          myFileWriter = null;
        }
      }

      @Override
      public void print(String text, ConsoleViewContentType contentType) {
        try {
          final DataOutputStream writer = getFileWriter();
          if (writer != null) {
            IOUtil.writeString(contentType.toString(), writer);
            IOUtil.writeString(text, writer);
          }
        }
        catch (FileNotFoundException e) {
          LOG.info(e);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }

      @Override
      public void printHyperlink(String text, HyperlinkInfo info) {
        if (info instanceof DiffHyperlink.DiffHyperlinkInfo) {
          final DiffHyperlink diffHyperlink = ((DiffHyperlink.DiffHyperlinkInfo)info).getPrintable();
          try {
            final DataOutputStream fileWriter = getFileWriter();
            if (fileWriter != null) {
              IOUtil.writeString(HYPERLINK, fileWriter);
              IOUtil.writeString(diffHyperlink.getLeft(), fileWriter);
              IOUtil.writeString(diffHyperlink.getRight(), fileWriter);
              IOUtil.writeString(diffHyperlink.getFilePath(), fileWriter);
            }
          }
          catch (FileNotFoundException e) {
            LOG.info(e);
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
        else {
          print(text, ConsoleViewContentType.NORMAL_OUTPUT);
        }
      }

      @Override
      public void onNewAvailable(@NotNull Printable printable) {}

      @Override
      public void mark() {}
    }

    private void readFileContentAndPrint(Printer printer, @Nullable File file, List<Printable> nestedPrintables) {
      if (file != null) {
        try {
          int lineNum = 0;
          Map<String, ConsoleViewContentType> contentTypeByNameMap = ContainerUtil.newMapFromValues(
            ConsoleViewContentType.getRegisteredTypes().iterator(), contentType -> contentType.toString()
          );
          DataInputStream reader = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
          try {
            while (reader.available() > 0 && !wasPrintableChanged(printer)) {
              if (lineNum == CompositePrintable.this.getExceptionMark() && lineNum > 0) printer.mark();
              final String firstToken = IOUtil.readString(reader);
              if (firstToken == null) break;
              if (firstToken.equals(HYPERLINK)) {
                new DiffHyperlink(IOUtil.readString(reader), IOUtil.readString(reader), IOUtil.readString(reader), false)
                  .printOn(printer);
              }
              else {
                ConsoleViewContentType contentType = contentTypeByNameMap.getOrDefault(firstToken, ConsoleViewContentType.NORMAL_OUTPUT);
                String text = IOUtil.readString(reader);
                printText(printer, text, contentType);
              }
              lineNum++;
            }
          }
          finally {
            reader.close();
          }
        }
        catch (FileNotFoundException e) {
          LOG.info(e);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      for (int i = 0; i < nestedPrintables.size(); i++) {
        if (i == getExceptionMark() && i > 0) printer.mark();
        nestedPrintables.get(i).printOn(printer);
      }
    }

    private void printText(Printer printer, String text, ConsoleViewContentType contentType) {
      if (ConsoleViewContentType.NORMAL_OUTPUT.equals(contentType)) {
        printer.printWithAnsiColoring(text, contentType);
      }
      else {
        printer.print(text, contentType);
      }
    }

    private boolean wasPrintableChanged(Printer printer) {
      return printer instanceof TestsOutputConsolePrinter && !((TestsOutputConsolePrinter)printer).isCurrent(CompositePrintable.this);
    }
  }

  private void printOutputFile(List<Printable> currentPrintables) {
    if (myOutputFile != null && new File(myOutputFile).exists()) {
      try {
        final PrintStream printStream = new PrintStream(new FileOutputStream(new File(myOutputFile), true));
        try {
          for (Printable currentPrintable : currentPrintables) {
            currentPrintable.printOn(new Printer() {
              @Override
              public void print(String text, ConsoleViewContentType contentType) {
                if (contentType != ConsoleViewContentType.SYSTEM_OUTPUT) {
                  printStream.print(text);
                }
              }

              @Override
              public void printHyperlink(String text, HyperlinkInfo info) {
                printStream.print(text);
              }

              @Override
              public void onNewAvailable(@NotNull Printable printable) {}
              @Override
              public void mark() {}
            });
          }
        }
        finally {
          printStream.close();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }
}
