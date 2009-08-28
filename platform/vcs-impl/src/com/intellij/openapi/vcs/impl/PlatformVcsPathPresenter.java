package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

/**
 * @author yole
 */
public class PlatformVcsPathPresenter extends VcsPathPresenter {
  public String getPresentableRelativePathFor(final VirtualFile file) {
    return FileUtil.toSystemDependentName(file.getPath());
  }

  public String getPresentableRelativePath(final ContentRevision fromRevision, final ContentRevision toRevision) {
    FilePath fromPath = fromRevision.getFile();
    FilePath toPath = toRevision.getFile();

    final RelativePathCalculator calculator =
      new RelativePathCalculator(toPath.getIOFile().getAbsolutePath(), fromPath.getIOFile().getAbsolutePath());
    calculator.execute();
    final String result = calculator.getResult();
    return (result == null) ? null : result.replace("/", File.separator);
  }
}
