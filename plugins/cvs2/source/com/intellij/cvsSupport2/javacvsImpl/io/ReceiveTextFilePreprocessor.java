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

  public void copyTextFileToLocation(File textFileSource, File targetFile, IReaderFactory readerFactory) throws IOException {
    VirtualFile virtualFile = CvsVfsUtil.refreshAndFindFileByIoFile(targetFile);
    if (myReceivedFileProcessor.shouldProcess(virtualFile, targetFile)) {
      PrintStream target = new PrintStream(new BufferedOutputStream(new FileOutputStream(targetFile)));
      try {
        String lineSeparator = getLineSeparatorFor(targetFile);
        Collection lines = new LineReader().readLines(new BufferedInputStream(new FileInputStream(textFileSource)));
        for (Iterator each = lines.iterator(); each.hasNext();) {
          target.write((byte[])each.next());
          if (each.hasNext()) target.print(lineSeparator);
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