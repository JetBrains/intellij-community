package org.jetbrains.javafx.sdk;

import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.facet.ExecutionModel;
import org.jetbrains.javafx.facet.JavaFxFacet;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxSdkUtil {
  private JavaFxSdkUtil() {
  }

  public static boolean isSdkHome(final String path) {
    final File file = getBinDirectory(path);
    if (!file.exists()) {
      return false;
    }
    final FileFilter fileFilter = new FileFilter() {
      public boolean accept(File file) {
        if (file.isDirectory()) {
          return false;
        }
        final String nameWithoutExtension = FileUtil.getNameWithoutExtension(file);
        return "javafx".equals(nameWithoutExtension) ||
               "javafxc".equals(nameWithoutExtension);
      }
    };
    final File[] files = file.listFiles(fileFilter);
    return files.length >= 2;
  }

  public static File getBinDirectory(String sdkHome) {
    return new File(sdkHome + "/bin");
  }

  @Nullable
  public static VirtualFile getLibDirectory(@NotNull final Sdk sdk) {
    final VirtualFile homeDirectory = sdk.getHomeDirectory();
    if (homeDirectory == null || !homeDirectory.exists()) {
      return null;
    }
    return homeDirectory.findChild("lib");
  }

  @NotNull
  public static VirtualFile[] findClasses(@NotNull final Sdk sdk) {
    final VirtualFile libDirectory = getLibDirectory(sdk);
    if (libDirectory == null) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final Set<VirtualFile> jarFiles = new HashSet<VirtualFile>();
    ContainerUtil.addAll(jarFiles, ExecutionModel.COMMON.getRoots(sdk));
    ContainerUtil.addAll(jarFiles, ExecutionModel.DESKTOP.getRoots(sdk));
    ContainerUtil.addAll(jarFiles, ExecutionModel.MOBILE.getRoots(sdk));
    ContainerUtil.addAll(jarFiles, ExecutionModel.TV.getRoots(sdk));
    ContainerUtil.addAll(jarFiles, ExecutionModel.PRISM.getRoots(sdk));
    if (jarFiles.size() == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }

    return VfsUtil.toVirtualFileArray(jarFiles);
  }

  @Nullable
  public static VirtualFile findInJar(@NotNull final VirtualFile jarFile, final String relativePath) {
    if (!jarFile.exists()) {
      return null;
    }
    final String url = JarFileSystem.PROTOCOL_PREFIX +
                 jarFile.getPath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR + relativePath;
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }

  @Nullable
  public static VirtualFile findSources(@NotNull final Sdk sdk) {
    final String homePath = sdk.getHomePath();
    VirtualFile jarFile = LocalFileSystem.getInstance().findFileByPath(homePath + "/src.jar");
    if (jarFile == null) {
      jarFile = LocalFileSystem.getInstance().findFileByPath(homePath + "/src.zip");
    }

    if (jarFile != null) {
      VirtualFile virtualFile = findInJar(jarFile, "src");
      if (virtualFile == null) {
        virtualFile = findInJar(jarFile, "");
      }
      return virtualFile;
    }
    return null;
  }

  // TODO:
  public static VirtualFile findDocs(final Sdk sdk, final String relativePath) {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }


  public static List<Sdk> getAllRelatedSdks() {
    final List<Sdk> result = new ArrayList<Sdk>();
    final Sdk[] sdks = ProjectJdkTable.getInstance().getAllJdks();
    for (Sdk sdk : sdks) {
      if (sdk.getSdkType() instanceof JavaFxSdkType) {
        result.add(sdk);
      }
    }
    return result;
  }

  @Nullable
  public static Sdk getSdk(@NotNull final JavaFxFacet facet) {
    return facet.getConfiguration().getJavaFxSdk();
  }

  public static boolean isHasValidSdk(final Module module) {
    final JavaFxFacet facet = FacetManager.getInstance(module).getFacetByType(JavaFxFacet.ID);
    if (facet == null) {
      return false;
    }
    final Sdk sdk = getSdk(facet);
    if (sdk != null && sdk.getSdkType() instanceof JavaFxSdkType) {
      return true;
    }
    return false;
  }

  public static void registerSdkRootListener(final Sdk sdk) {
    if (sdk != null && sdk.getUserData(JavaFxSdkRootsListener.JAVAFX_SDK_ROOTS_LISTENER_KEY) == null) {
      final JavaFxSdkRootsListener listener = new JavaFxSdkRootsListener(sdk);
      sdk.getRootProvider().addRootSetChangedListener(listener);
      sdk.putUserData(JavaFxSdkRootsListener.JAVAFX_SDK_ROOTS_LISTENER_KEY, listener);
    }
  }
}
