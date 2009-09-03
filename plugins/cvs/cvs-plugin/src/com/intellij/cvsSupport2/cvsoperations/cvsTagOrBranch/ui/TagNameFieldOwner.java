package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui;

public interface TagNameFieldOwner {
  void enableOkAction();

  void disableOkAction(String errorMeesage);

  boolean tagFieldIsActive();
}