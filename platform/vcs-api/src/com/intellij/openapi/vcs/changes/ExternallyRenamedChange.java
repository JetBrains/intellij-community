// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class ExternallyRenamedChange extends Change {
  private String myRenamedTargetName;
  private boolean myCopied;
  private final String myOriginUrl;

  public ExternallyRenamedChange(final ContentRevision beforeRevision, final ContentRevision afterRevision, final String originUrl) {
    super(beforeRevision, afterRevision);
    myOriginUrl = originUrl;
    myCopied = true;
  }

  public void setRenamedOrMovedTarget(final FilePath target) {
    myMoved = myRenamed = false;

    if ((getBeforeRevision() != null) || (getAfterRevision() == null)) {
      // not external rename or move
      return;
    }
    final FilePath localPath = ChangesUtil.getFilePath(this);
    if (localPath.getPath().equals(target.getPath())) {
      // not rename or move
      return;
    }

    if (Comparing.equal(target.getParentPath(), localPath.getParentPath())) {
      myRenamed = true;
    } else {
      myMoved = true;
    }
    myCopied = false;

    myRenamedTargetName = target.getName();
    myRenameOrMoveCached = true;
  }

  @Override
  @Nls
  public String getOriginText(final Project project) {
    if (myCopied) {
      return VcsBundle.message("change.file.copied.from.text", myOriginUrl);
    }
    return super.getOriginText(project);
  }

  @Override
  @Nullable
  @Nls
  protected String getRenamedText() {
    if (myRenamedTargetName != null) {
      if (getBeforeRevision() != null) {
        return VcsBundle.message("change.file.renamed.to.text", myRenamedTargetName);
      }
      else {
        return VcsBundle.message("change.file.renamed.from.text", myRenamedTargetName);
      }
    }
    return super.getRenamedText();
  }

  @Override
  @Nullable
  @Nls
  protected String getMovedText(final Project project) {
    if (myRenamedTargetName != null) {
      if (getBeforeRevision() != null) {
        return VcsBundle.message("change.file.moved.to.text", myRenamedTargetName);
      }
      else {
        return VcsBundle.message("change.file.moved.from.text", myRenamedTargetName);
      }
    }
    return super.getMovedText(project);
  }

  public boolean isCopied() {
    return myCopied;
  }

  public void setCopied(final boolean copied) {
    myCopied = copied;
  }

  public String getOriginUrl() {
    return myOriginUrl;
  }
}
