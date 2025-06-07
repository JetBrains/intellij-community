// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.impl.VcsFileStatusInfo;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;

@ApiStatus.Internal
public final class GitLogFullRecord extends GitLogRecord {
  private final @NotNull List<VcsFileStatusInfo> myStatusInfo;

  GitLogFullRecord(@NotNull Map<GitLogParser.GitLogOption, String> options,
                   @NotNull List<VcsFileStatusInfo> statusInfo,
                   boolean supportsRawBody) {
    super(options, supportsRawBody);
    myStatusInfo = statusInfo;
  }

  private @NotNull Collection<String> getPaths() {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (VcsFileStatusInfo info : myStatusInfo) {
      result.add(info.getFirstPath());
      if (info.getSecondPath() != null) result.add(info.getSecondPath());
    }
    return result;
  }

  @NotNull
  List<VcsFileStatusInfo> getStatusInfos() {
    return myStatusInfo;
  }

  @VisibleForTesting
  public @NotNull List<FilePath> getFilePaths(@NotNull VirtualFile root) {
    List<FilePath> res = new ArrayList<>();
    String prefix = root.getPath() + "/";
    for (String strPath : getPaths()) {
      res.add(VcsUtil.getFilePath(prefix + strPath, false));
    }
    return res;
  }

  @VisibleForTesting
  public @NotNull List<Change> parseChanges(@NotNull Project project, @NotNull VirtualFile vcsRoot) {
    String[] hashes = getParentsHashes();
    return GitChangesParser.parse(project, vcsRoot, myStatusInfo, getHash(), getDate(), hashes.length == 0 ? null : hashes[0]);
  }

  @Override
  public String toString() {
    return String.format("GitLogRecord{myOptions=%s, myStatusInfo=%s, mySupportsRawBody=%s, myHandler=%s}",
                         myOptions, myStatusInfo, mySupportsRawBody, myHandler);
  }
}
