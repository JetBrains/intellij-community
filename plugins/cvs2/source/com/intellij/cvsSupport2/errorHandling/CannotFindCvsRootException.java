package com.intellij.cvsSupport2.errorHandling;

import java.io.File;

/**
 * author: lesya
 */

public class CannotFindCvsRootException extends Exception{
    public CannotFindCvsRootException(File file) {
      super("Cannot find CVSROOT for file " + file);
    }
  }
