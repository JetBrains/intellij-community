package org.jetbrains.jps.android.builder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;

import java.io.File;

/**
* @author Eugene.Kudelevsky
*/
class AndroidFileBasedBuildRootDescriptor extends BuildRootDescriptorImpl {
  public AndroidFileBasedBuildRootDescriptor(@NotNull BuildTarget target, @NotNull File file) {
    super(target, file);
  }
}
