package com.intellij.cvsSupport2.javacvsImpl.io;

import org.netbeans.lib.cvsclient.file.ISendTextFilePreprocessor;
import org.netbeans.lib.cvsclient.file.IWriterFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import com.intellij.util.text.LineReader;

/**
 * author: lesya
 */
public class SendTextFilePreprocessor implements ISendTextFilePreprocessor {
  public File getPreprocessedTextFile(File originalTextFile, IWriterFactory writerFactory) throws IOException {
    final File preprocessedTextFile = File.createTempFile("send", null);
    Collection lines = new LineReader().readLines(new FileInputStream(originalTextFile));

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

  public void cleanup(File preprocessedTextFile) {
    preprocessedTextFile.delete();
  }
}
