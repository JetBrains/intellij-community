package com.intellij.cvsSupport2.actions.cvsContext;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.openapi.vcs.actions.VcsContext;

import java.io.File;
import java.util.Collection;

public interface CvsContext extends VcsContext {

  boolean cvsIsActive();

  Collection<String> getDeletedFileNames();

  String getFileToRestore();

  CvsLightweightFile getCvsLightweightFile();

  CvsLightweightFile[] getSelectedLightweightFiles();

  CvsEnvironment getEnvironment();

  Collection<AddedFileInfo> getAllFilesToAdd();

  File getSomeSelectedFile();
}
