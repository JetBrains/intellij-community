package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesListener;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsCompositeListener;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsCompositeListener;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesListener;

/**
 * author: lesya
 */
public abstract class AbstractCvsHandler extends CvsHandler {
  protected final CvsCompositeListener myCompositeListener = new CvsCompositeListener();

  public AbstractCvsHandler(String title, FileSetToBeUpdated files) {
    super(title, files);
  }

  public void addCvsListener(CvsMessagesListener listener) {
    myCompositeListener.addCvsListener(listener);
  }

  public void removeCvsListener(CvsMessagesListener listener) {
    myCompositeListener.removeCvsListener(listener);
  }
}
