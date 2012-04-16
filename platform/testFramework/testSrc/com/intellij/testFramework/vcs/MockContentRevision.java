
package com.intellij.testFramework.vcs;

import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class MockContentRevision implements ContentRevision {
  private final FilePath myPath;
  private final VcsRevisionNumber myRevisionNumber;

  public MockContentRevision(final FilePath path, final VcsRevisionNumber revisionNumber) {
    myPath = path;
    myRevisionNumber = revisionNumber;
  }

  @Override
  @Nullable
  public String getContent() throws VcsException {
    return null;
  }

  @Override
  @NotNull
  public FilePath getFile() {
    return myPath;
  }

  @Override
  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  @Override
  public String toString() {
    return myPath.getName() + ":" + myRevisionNumber;
  }
}
