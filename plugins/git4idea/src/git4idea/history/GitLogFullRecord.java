// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.impl.VcsFileStatusInfo;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class GitLogFullRecord extends GitLogRecord {

  @NotNull private final List<? extends VcsFileStatusInfo> myStatusInfo;

  GitLogFullRecord(@NotNull Map<GitLogParser.GitLogOption, String> options,
                   @NotNull List<? extends VcsFileStatusInfo> statusInfo,
                   boolean supportsRawBody) {
    super(options, supportsRawBody);
    myStatusInfo = statusInfo;
  }

  @NotNull
  private Collection<String> getPaths() {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (VcsFileStatusInfo info : myStatusInfo) {
      result.add(info.getFirstPath());
      if (info.getSecondPath() != null) result.add(info.getSecondPath());
    }
    return result;
  }

  @NotNull
  List<? extends VcsFileStatusInfo> getStatusInfos() {
    return myStatusInfo;
  }

  @NotNull
  List<FilePath> getFilePaths(@NotNull VirtualFile root) {
    List<FilePath> res = new ArrayList<>();
    String prefix = root.getPath() + "/";
    for (String strPath : getPaths()) {
      res.add(VcsUtil.getFilePath(prefix + strPath, false));
    }
    return res;
  }

  @NotNull
  List<Change> parseChanges(@NotNull Project project, @NotNull VirtualFile vcsRoot) throws VcsException {
    String[] hashes = getParentsHashes();
    return GitChangesParser.parse(project, vcsRoot, myStatusInfo, getHash(), getDate(), hashes.length == 0 ? null : hashes[0]);
  }

  @Override
  public String toString() {
    return String.format("GitLogRecord{myOptions=%s, myStatusInfo=%s, mySupportsRawBody=%s, myHandler=%s}",
                         myOptions, myStatusInfo, mySupportsRawBody, myHandler);
  }
}
