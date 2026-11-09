package com.intellij.driver.model;

public enum LockSemantics {
  NO_LOCK,
  READ_ACTION,
  /**
   *  If combined with {@link OnDispatcher#EDT}, the corresponding call will be executed in the non-modal context,
   *  since write actions are prohibited in 'any' modality.
   */
  WRITE_ACTION
}
