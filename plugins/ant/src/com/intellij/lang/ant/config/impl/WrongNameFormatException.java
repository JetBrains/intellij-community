package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.resources.AntBundle;


public final class WrongNameFormatException extends Exception {
  public WrongNameFormatException(final String name) {
    super(AntBundle.message("execute.target.wrong.name.format.error.message", name));
  }
}