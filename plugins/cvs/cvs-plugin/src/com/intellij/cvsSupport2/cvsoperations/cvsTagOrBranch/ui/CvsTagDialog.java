package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui;

import com.intellij.openapi.ui.DialogWrapper;

/**
 * author: lesya
 */
public abstract class CvsTagDialog extends DialogWrapper implements TagNameFieldOwner {
  public CvsTagDialog() {
    super(true);
  }

  public void enableOkAction(){
    setOKActionEnabled(true);
  }

  public void disableOkAction(String errorMeesage){
    setOKActionEnabled(false);
  }

}
