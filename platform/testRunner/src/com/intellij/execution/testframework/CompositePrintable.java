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
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class CompositePrintable implements Printable, Disposable {
  public static final String NEW_LINE = "\n";

  protected final List<Printable> myNestedPrintables = new ArrayList<Printable>();
  private PrintablesWrapper myWrapper;
  protected int myExceptionMark;

  public void flush() {
    if (myWrapper == null) {
      try {
        myWrapper = new PrintablesWrapper(File.createTempFile("frst", "scd"));
      }
      catch (IOException ignored) {
      }
    }
    if (myWrapper != null) {
      synchronized (myNestedPrintables) {
        myWrapper.flash(myNestedPrintables);
        clear();
      }
    }
  }

  public void printOn(final Printer printer) {
    if (myWrapper != null) {
      myWrapper.printOn(printer);
    }
    synchronized (myNestedPrintables) {
      for (int i = 0; i < myNestedPrintables.size(); i++) {
        if (i == getExceptionMark() && i > 0) printer.mark();
        myNestedPrintables.get(i).printOn(printer);
      }
    }
  }

  public void addLast(final Printable printable) {
    synchronized (myNestedPrintables) {
      myNestedPrintables.add(printable);
    }
  }

  protected void clear() {
    synchronized (myNestedPrintables) {
      myNestedPrintables.clear();
    }
  }

  public int getCurrentSize() {
    return myNestedPrintables.size();
  }

  @Override
  public void dispose() {
    clear();
    if (myWrapper != null) {
      myWrapper.dispose();
    }
  }

  public int getExceptionMark() {
    return myExceptionMark;
  }

  public void setExceptionMark(int exceptionMark) {
    myExceptionMark = exceptionMark;
  }

  private static final Logger LOG = Logger.getInstance("#" + PrintablesWrapper.class.getName());

  private class PrintablesWrapper {

    @NonNls private static final String HYPERLINK = "hyperlink";
    private DataInputStream myReader;
    private ConsoleViewContentType myLastSelected;
    private final File myFile;

    PrintablesWrapper(File file) {
      myFile = file;
    }

    public void flash(List<Printable> printables) {
      final DataOutputStream fileWriter;
      try {
        fileWriter = new DataOutputStream(new FileOutputStream(myFile, true));

      }
      catch (FileNotFoundException e) {
        LOG.error(e);
        return;
      }
      try {
        for (final Printable printable : printables) {
          printable.printOn(new Printer() {
            @Override
            public void print(String text, ConsoleViewContentType contentType) {
              try {
                IOUtil.writeString(contentType.toString() + text, fileWriter);
              }
              catch (IOException e) {
                LOG.error(e);
              }
            }

            @Override
            public void onNewAvailable(Printable printable11) {
            }

            @Override
            public void printHyperlink(String text, HyperlinkInfo info) {
              if (info instanceof DiffHyperlink.DiffHyperlinkInfo) {
                final DiffHyperlink diffHyperlink = ((DiffHyperlink.DiffHyperlinkInfo)info).getPrintable();
                try {
                  IOUtil.writeString(HYPERLINK, fileWriter);
                  IOUtil.writeString(diffHyperlink.getLeft(), fileWriter);
                  IOUtil.writeString(diffHyperlink.getRight(), fileWriter);
                  IOUtil.writeString(diffHyperlink.getFilePath(), fileWriter);
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
            public void mark() {
            }
          });
        }
      }
      finally {
        try {
          fileWriter.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    public void printOn(Printer console) {
      try {
        myReader = new DataInputStream(new FileInputStream(myFile));
        int lineNum = 0;
        while (myReader.available() > 0) {
          if (lineNum == CompositePrintable.this.getExceptionMark() && lineNum > 0) console.mark();
          final String line = IOUtil.readString(myReader);
          if (!isApplicable(console, line, ConsoleViewContentType.ERROR_OUTPUT)) {
            if (!isApplicable(console, line, ConsoleViewContentType.SYSTEM_OUTPUT)) {
              if (!isApplicable(console, line, ConsoleViewContentType.NORMAL_OUTPUT)) {
                if (line.startsWith(HYPERLINK)) {
                  new DiffHyperlink(IOUtil.readString(myReader), IOUtil.readString(myReader), IOUtil.readString(myReader)).printOn(console);
                }
                else {
                  console.print(line, myLastSelected != null ? myLastSelected : ConsoleViewContentType.NORMAL_OUTPUT);
                }
              }
            }
          }
          lineNum++;
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        try {
          if (myReader != null) {
            myReader.close();
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    private boolean isApplicable(Printer console, String line, ConsoleViewContentType contentType) {
      final String prefix = contentType.toString();
      if (line.startsWith(prefix)) {
        console.print(line.substring(prefix.length()), contentType);
        myLastSelected = contentType;
        return true;
      }
      return false;
    }

    public void dispose() {
      FileUtil.delete(myFile);
    }
  }
}

