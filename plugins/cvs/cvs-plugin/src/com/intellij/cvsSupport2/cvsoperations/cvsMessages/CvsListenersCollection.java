package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

import java.util.Collection;

public interface CvsListenersCollection {
  void addCvsListener(CvsMessagesListener listener);
}
