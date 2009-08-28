package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.util.JDOMExternalizable;

/**
 * @author yole
 */
public interface ExternalizableFileType extends JDOMExternalizable {
  void markDefaultSettings();
  boolean isModified();
}