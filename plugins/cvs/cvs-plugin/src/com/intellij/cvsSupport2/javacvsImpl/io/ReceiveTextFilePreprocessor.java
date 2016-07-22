/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.cvsoperations.common.ReceivedFileProcessor;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.HashMap;
import org.netbeans.lib.cvsclient.file.IReaderFactory;
import org.netbeans.lib.cvsclient.file.IReceiveTextFilePreprocessor;

import java.io.*;
import java.nio.charset.Charset;
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
            target.write(charSet.encode(CharsetToolkit.bytesToString(line, CharsetToolkit.UTF8_CHARSET)).array());
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
      return CodeStyleSettingsManager.getInstance().getCurrentSettings().getLineSeparator();
    }
  }

  public void saveLineSeparatorForFile(VirtualFile virtualFile, String lineSeparatorFor) {
    myFileToSeparator.put(CvsVfsUtil.getFileFor(virtualFile), lineSeparatorFor);
  }
}
