package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public interface WritingAccessProvider {

  ExtensionPointName<WritingAccessProvider> EP_NAME = ExtensionPointName.create("com.intellij.writingAccessProvider");

  /**
   * @param files files to be checked
   * @return set of files that cannot be accessed
   */
  @NotNull
  Collection<VirtualFile> requestWriting(VirtualFile... files);
}
