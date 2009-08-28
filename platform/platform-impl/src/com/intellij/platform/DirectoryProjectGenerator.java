package com.intellij.platform;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.Nls;

/**
 * @author yole
 */
public interface DirectoryProjectGenerator<T> {
  ExtensionPointName<DirectoryProjectGenerator> EP_NAME = ExtensionPointName.create("com.intellij.directoryProjectGenerator");

  @Nls
  String getName();

  T showGenerationSettings(final VirtualFile baseDir) throws ProcessCanceledException;

  void generateProject(final Project project, final VirtualFile baseDir, final T settings);
}
