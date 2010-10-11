package org.jetbrains.javafx.facet;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.sdk.JavaFxSdkUtil;

import java.util.Collection;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
// TODO: adding Jars
public enum ExecutionModel {
  COMMON("shared", "javafxrt.jar"),
  DESKTOP("desktop"),
  MOBILE("mobile"),
  TV("tv"),
  PRISM("prism");

  private final String myDirectoryName;
  private final Set<String> myRootsNames;
  private final VirtualFileFilter myFileFilter;

  ExecutionModel(@NotNull final String directoryName, @NotNull final String... rootsNames) {
    myDirectoryName = directoryName;
    myRootsNames = new HashSet<String>();
    ContainerUtil.addAll(myRootsNames, rootsNames);
    myFileFilter = new VirtualFileFilter() {
      public boolean accept(final VirtualFile f) {
        if (f.isDirectory()) {
          return false;
        }
        if (myRootsNames.contains(f.getName())) {
          return true;
        }
        return false;
      }
    };
  }

  ExecutionModel(@NotNull final String directoryName) {
    myDirectoryName = directoryName;
    myRootsNames = null;
    myFileFilter = new VirtualFileFilter() {
      public boolean accept(final VirtualFile f) {
        if (f.isDirectory()) {
          return false;
        }
        if (StringUtil.endsWith(f.getName(), ".jar")) {
          return true;
        }
        return false;
      }
    };
  }

  @NotNull
  public VirtualFile[] getRoots(@NotNull final Sdk sdk) {
    final VirtualFile libDirectory = JavaFxSdkUtil.getLibDirectory(sdk);
    if (libDirectory == null) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final VirtualFile profileDirectory = libDirectory.findChild(myDirectoryName);
    if (profileDirectory == null) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final Collection<VirtualFile> res = new HashSet<VirtualFile>();
    for (VirtualFile file : profileDirectory.getChildren()) {
      if (myFileFilter.accept(file)) {
        res.add(JavaFxSdkUtil.findInJar(file, ""));
      }
    }
    return VfsUtil.toVirtualFileArray(res);
  }
}
