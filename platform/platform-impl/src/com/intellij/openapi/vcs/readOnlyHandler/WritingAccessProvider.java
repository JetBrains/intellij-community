package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public interface WritingAccessProvider {

  ExtensionPointName<WritingAccessProvider> EP_NAME = ExtensionPointName.create("com.intellij.writingAccessProvider");
  
  boolean isWritingAllowed(@NotNull VirtualFile file);

  boolean requestWriting(Collection<VirtualFile> files);
}
