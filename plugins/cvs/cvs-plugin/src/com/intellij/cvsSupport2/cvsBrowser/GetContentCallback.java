package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;

import java.util.List;

/**
 * author: lesya
 */
public interface GetContentCallback {
  void appendDirectoryContent(List<CvsElement> directoryContent);

  void fillDirectoryContent(List<CvsElement> directoryContent);

  void loginAborted();

  void finished();

  void useForCancel(final CvsListenerWithProgress listener);
}
