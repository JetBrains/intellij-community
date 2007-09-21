package com.intellij.cvsSupport2.cvsBrowser;

import java.util.List;

/**
 * author: lesya
 */
public interface GetContentCallback {
  void fillDirectoryContent(List<CvsElement> directoryContent);

  void loginAborted();

  void finished();
}
