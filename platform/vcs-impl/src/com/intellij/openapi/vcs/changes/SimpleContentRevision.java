
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
*/
public class SimpleContentRevision implements ContentRevision {
  private final String myContent;
  private final FilePath myNewFilePath;
  private final String myRevision;

  public SimpleContentRevision(final String content, final FilePath newFilePath, final String revision) {
    myContent = content;
    myNewFilePath = newFilePath;
    myRevision = revision;
  }

  @Nullable
  public String getContent() {
    return myContent;
  }

  @NotNull
  public FilePath getFile() {
    return myNewFilePath;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return new VcsRevisionNumber() {
      public String asString() {
        return myRevision;
      }

      public int compareTo(final VcsRevisionNumber o) {
        return 0;
      }
    };
  }
}