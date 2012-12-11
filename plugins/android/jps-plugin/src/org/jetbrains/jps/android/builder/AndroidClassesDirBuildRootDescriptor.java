package org.jetbrains.jps.android.builder;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

import java.io.File;
import java.io.FileFilter;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidClassesDirBuildRootDescriptor extends BuildRootDescriptor {
  private final BuildTarget myTarget;
  private final File myRoot;

  public AndroidClassesDirBuildRootDescriptor(@NotNull BuildTarget target, @NotNull File root) {
    myTarget = target;
    myRoot = root;
  }

  @Override
  public String getRootId() {
    return FileUtil.toSystemIndependentName(myRoot.getAbsolutePath());
  }

  @Override
  public File getRootFile() {
    return myRoot;
  }

  @Override
  public BuildTarget<?> getTarget() {
    return myTarget;
  }

  @Override
  public FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor) {
    return new FileFilter() {
      @Override
      public boolean accept(File file) {
        return "class".equals(FileUtil.getExtension(file.getName()));
      }
    };
  }
}
