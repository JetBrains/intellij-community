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
class AndroidFileBasedBuildRootDescriptor extends BuildRootDescriptor {
  private final File myFile;
  private BuildTarget myTarget;

  public AndroidFileBasedBuildRootDescriptor(@NotNull BuildTarget target, @NotNull File file) {
    myTarget = target;
    myFile = file;
  }

  @Override
  public String getRootId() {
    return FileUtil.toSystemIndependentName(myFile.getAbsolutePath());
  }

  @Override
  public File getRootFile() {
    return myFile;
  }

  @Override
  public BuildTarget<?> getTarget() {
    return myTarget;
  }

  @Override
  public FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor) {
    return new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return true;
      }
    };
  }
}
