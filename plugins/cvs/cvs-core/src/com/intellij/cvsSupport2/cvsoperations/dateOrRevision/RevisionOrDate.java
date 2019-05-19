// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsoperations.dateOrRevision;

import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.command.Command;


/**
 * author: lesya
 */
public interface RevisionOrDate {
  RevisionOrDate EMPTY = new RevisionOrDate() {
    @Override
    public void setForCommand(Command command) {
    }

    @Override
    public String getRevision() {
      return "HEAD";
    }

    @Override
    public CvsRevisionNumber getCvsRevisionNumber() {
      return null;
    }
  };

  void setForCommand(Command command);

  @NonNls String getRevision();

  CvsRevisionNumber getCvsRevisionNumber();
}
