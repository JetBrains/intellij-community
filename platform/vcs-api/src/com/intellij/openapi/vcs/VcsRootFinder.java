package com.intellij.openapi.vcs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 7/18/13 3:53 PM
 */
public interface VcsRootFinder {

  ExtensionPointName<VcsRootFinder> EP_NAME = ExtensionPointName.create("com.intellij.vcs.rootFinder");

  /**
   * Tries to find VCS roots which are located at or below given directory.
   *
   * @param root  root directory to start looking for VCS roots
   * @return collection of VCS root info found at or below given directory
   */
  @NotNull
  Collection<VcsDirectoryMapping> findRoots(@NotNull VirtualFile root);
}
