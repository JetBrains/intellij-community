package com.intellij.cvsSupport2.javacvsImpl;

import org.netbeans.lib.cvsclient.file.IFileReadOnlyHandler;

import java.io.File;
import java.io.IOException;

import com.intellij.util.io.ReadOnlyAttributeUtil;

/**
 * author: lesya
 */
public class FileReadOnlyHandler implements IFileReadOnlyHandler{
  public void setFileReadOnly(File file, boolean readOnly) throws IOException {
    if (file.canWrite() != readOnly) return;
    ReadOnlyAttributeUtil.setReadOnlyAttribute(file.getAbsolutePath(), readOnly);
  }
}
