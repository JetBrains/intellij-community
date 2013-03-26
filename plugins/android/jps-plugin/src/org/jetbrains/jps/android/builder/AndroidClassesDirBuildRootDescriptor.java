package org.jetbrains.jps.android.builder;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;

import java.io.File;
import java.io.FileFilter;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidClassesDirBuildRootDescriptor extends BuildRootDescriptorImpl {
  public AndroidClassesDirBuildRootDescriptor(@NotNull BuildTarget target, @NotNull File root) {
    super(target, root);
  }

  @NotNull
  @Override
  public FileFilter createFileFilter() {
    return new FileFilter() {
      @Override
      public boolean accept(@NotNull File file) {
        return FileUtilRt.extensionEquals(file.getName(), "class");
      }
    };
  }
}
