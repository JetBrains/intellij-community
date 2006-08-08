package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.cvsSupport2.cvsoperations.common.ReceivedFileProcessor;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.LineReader;
import org.netbeans.lib.cvsclient.file.IReaderFactory;
import org.netbeans.lib.cvsclient.file.IReceiveTextFilePreprocessor;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * author: lesya
 */
public class ReceiveTextFilePreprocessor implements IReceiveTextFilePreprocessor {
  private final ReceivedFileProcessor myReceivedFileProcessor;
  private final Map<File, String> myFileToSeparator = new HashMap<File, String>();

  public ReceiveTextFilePreprocessor(ReceivedFileProcessor receivedFileProcessor) {
    myReceivedFileProcessor = receivedFileProcessor;
  }

  public void copyTextFileToLocation(File textFileSource, File targetFile, IReaderFactory readerFactory, Charset charSet) throws IOException {
    Charset utf8Charset = Charset.forName("UTF-8");
    VirtualFile virtualFile = CvsVfsUtil.findFileByIoFile(targetFile);
    if (myReceivedFileProcessor.shouldProcess(virtualFile, targetFile)) {
      PrintStream target = new PrintStream(new BufferedOutputStream(new FileOutputStream(targetFile)));
      try {
        String lineSeparator = getLineSeparatorFor(targetFile);
        byte[] lineSeparatorBytes = null;
        if (charSet != null) {
          lineSeparatorBytes = charSet.encode(lineSeparator).array();
        }
        final BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(textFileSource));
        try {
          Collection<byte[]> lines = new LineReader(inputStream).readLines();
          for (Iterator<byte[]> each = lines.iterator(); each.hasNext();) {
            final byte[] bytes = each.next();
            if (charSet == null) {
              target.write(bytes);
            }
            else {
              target.write(charSet.encode(utf8Charset.decode(ByteBuffer.wrap(bytes))).array());
            }
            if (each.hasNext()) {
              if (charSet == null)
                target.print(lineSeparator);
              else
                target.write(lineSeparatorBytes);
            }
          }
        }
        finally {
          inputStream.close();
        }
      }
      finally {
        target.close();
      }
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