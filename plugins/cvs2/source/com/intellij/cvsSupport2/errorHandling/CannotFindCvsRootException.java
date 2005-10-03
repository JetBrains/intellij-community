package com.intellij.cvsSupport2.errorHandling;

import java.io.File;
import java.text.MessageFormat;

/**
 * author: lesya
 */

public class CannotFindCvsRootException extends Exception{
    public CannotFindCvsRootException(File file) {
      super(com.intellij.CvsBundle.message("exception.text.cannot.find.cvsroot.for.file", file));
    }
  }
