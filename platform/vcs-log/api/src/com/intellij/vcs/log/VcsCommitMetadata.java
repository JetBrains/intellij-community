package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Full details of a commit: all metadata (commit message, author, committer, etc.) but without changes.
 * These details will be shown in dedicated panels displayed near the log, and can be used for in-memory filtering.
 * <p/>
 * An instance of this object can be obtained via
 * {@link VcsLogObjectsFactory#createCommitMetadata(Hash, List, long, VirtualFile, String, String, String, String, String, String,long)
 * VcsLogObjectsFactory#createCommitMetadata}
 * <p/>
 * It is not recommended to create a custom implementation of this interface, but if you need it, <b>make sure to implement {@code equals()}
 * and {@code hashcode()} so that they consider only the Hash</b>, i.e. two VcsCommitMetadatas are equal if and only if they have equal
 * hash codes. The VCS Log framework heavily relies on this fact.
 */
public interface VcsCommitMetadata extends VcsShortCommitDetails {
  @NotNull
  String getFullMessage();
}
