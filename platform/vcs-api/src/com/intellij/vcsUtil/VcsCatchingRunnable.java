// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.SmartList;

import java.util.List;
import java.util.function.Supplier;

/**
 * @author irengrig
 */
public abstract class VcsCatchingRunnable implements Runnable, Supplier<List<VcsException>> {
  private final List<VcsException> myExceptions;

  public VcsCatchingRunnable() {
    myExceptions = new SmartList<>();
  }

  @Override
  public List<VcsException> get() {
    return myExceptions;
  }

  protected abstract void runImpl() throws VcsException;

  @Override
  public void run() {
    try {
      runImpl();
    }
    catch (VcsException e) {
      myExceptions.add(e);
    }
  }
}
