package com.intellij.cvsSupport2.history;

import com.intellij.openapi.vcs.history.VcsFileRevision;

import java.util.Collection;

/**
 * author: lesya
 */
public interface CvsFileRevision extends VcsFileRevision{

  String getBranches();

  String getState();

  Collection getTags();

}
