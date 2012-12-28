package org.jetbrains.jps.android.builder;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

import java.io.File;
import java.io.FileFilter;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidClassesDirBuildRootDescriptor extends BuildRootDescriptorImpl {
  public AndroidClassesDirBuildRootDescriptor(@NotNull BuildTarget target, @NotNull File root) {
    super(target, root);
  }

  @Override
  public FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor) {
    return new FileFilter() {
      @Override
      public boolean accept(@NotNull File file) {
        return "class".equals(FileUtil.getExtension(file.getName()));
      }
    };
  }
}
