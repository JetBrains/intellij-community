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
package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.text.LineReader;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.file.ISendTextFilePreprocessor;
import org.netbeans.lib.cvsclient.file.IWriterFactory;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;

/**
 * author: lesya
 */
public class SendTextFilePreprocessor implements ISendTextFilePreprocessor {
  @NonNls private static final String TEMP_FILE_PREFIX = "send";

  @Override
  public File getPreprocessedTextFile(File originalTextFile, IWriterFactory writerFactory) throws IOException {
    final File preprocessedTextFile = FileUtil.createTempFile(TEMP_FILE_PREFIX, null);
    final BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(originalTextFile));
    Collection lines;
    try {
      lines = new LineReader(inputStream).readLines();
    }
    finally {
      inputStream.close();
    }

    FileOutputStream output = new FileOutputStream(preprocessedTextFile);

    try {
      for (Iterator each = lines.iterator(); each.hasNext();) {
        output.write((byte[]) each.next());
        if (each.hasNext()){
          output.write('\n');
        }
      }
    } finally {
      output.close();
    }

    return preprocessedTextFile;
  }

  @Override
  public void cleanup(File preprocessedTextFile) {
    preprocessedTextFile.delete();
  }
}
