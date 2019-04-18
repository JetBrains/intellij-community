// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.application.options.CodeStyle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.cvsoperations.common.ReceivedFileProcessor;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.file.IReaderFactory;
import org.netbeans.lib.cvsclient.file.IReceiveTextFilePreprocessor;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * author: lesya
 */
public class ReceiveTextFilePreprocessor implements IReceiveTextFilePreprocessor {
  private final ReceivedFileProcessor myReceivedFileProcessor;
  private final Map<File, String> myFileToSeparator = new HashMap<>();

  public ReceiveTextFilePreprocessor(ReceivedFileProcessor receivedFileProcessor) {
    myReceivedFileProcessor = receivedFileProcessor;
  }

  @Override
  public void copyTextFileToLocation(InputStream textFileSource, int length, File targetFile, IReaderFactory readerFactory, Charset charSet) throws IOException {
    final VirtualFile virtualFile = CvsVfsUtil.findFileByIoFile(targetFile);
    if (myReceivedFileProcessor.shouldProcess(virtualFile, targetFile)) {
      final PrintStream target = new PrintStream(new BufferedOutputStream(new FileOutputStream(targetFile)));
      try {
        final String lineSeparator = getLineSeparatorFor(targetFile);
        byte[] lineSeparatorBytes = null;
        if (charSet != null) {
          lineSeparatorBytes = charSet.encode(lineSeparator).array();
        }
        final LineReader lineReader = new LineReader(textFileSource, length);
        boolean first = true;
        for (byte[] line = lineReader.readLine(); line != null; line = lineReader.readLine()) {
          if (!first) {
            if (charSet == null)
              target.print(lineSeparator);
            else
              target.write(lineSeparatorBytes);
          } else {
            first = false;
          }
          if (charSet == null) {
            target.write(line);
          }
          else {
            target.write(charSet.encode(CharsetToolkit.bytesToString(line, StandardCharsets.UTF_8)).array());
          }
        }
      }
      finally {
        target.close();
      }
    } else {
      // read file from server, but do not save it.
      CvsUtil.skip(textFileSource, length);
    }
  }

  private String getLineSeparatorFor(File file) {
    if (myFileToSeparator.containsKey(file)) {
      return myFileToSeparator.get(file);
    }
    else {
      return CodeStyle.getDefaultSettings().getLineSeparator();
    }
  }

  public void saveLineSeparatorForFile(VirtualFile virtualFile, String lineSeparatorFor) {
    myFileToSeparator.put(CvsVfsUtil.getFileFor(virtualFile), lineSeparatorFor);
  }
}
