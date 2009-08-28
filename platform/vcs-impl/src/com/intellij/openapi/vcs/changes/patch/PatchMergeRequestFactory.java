package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PatchMergeRequestFactory {
  MergeRequest createMergeRequest(String leftText, String rightText, String originalContent, @NotNull VirtualFile file, Project project);
}
