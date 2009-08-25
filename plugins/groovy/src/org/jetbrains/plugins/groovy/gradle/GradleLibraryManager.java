package org.jetbrains.plugins.groovy.gradle;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.AbstractGroovyLibraryManager;

import javax.swing.*;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class GradleLibraryManager extends AbstractGroovyLibraryManager {
  public static final Icon GRADLE_ICON = IconLoader.getIcon("/icons/gradle/gradle.png");
  @NonNls private static final Pattern GRADLE_JAR_FILE_PATTERN = Pattern.compile("gradle-(core-)?(\\d.*)\\.jar");

  @NotNull
  @Override
  public Icon getIcon() {
    return GRADLE_ICON;
  }

  @Nls
  @Override
  public String getLibraryVersion(@NotNull Library library, LibrariesContainer container) {
    return getGradleVersion(container.getLibraryFiles(library, OrderRootType.CLASSES));
  }

  @Nullable
  private static String getGradleVersion(VirtualFile[] libraryFiles) {
    for (VirtualFile file : libraryFiles) {
      final String version = getGradleJarVersion(file);
      if (version != null) {
        return version;
      }
    }
    return null;
  }


  @Override
  public boolean isSDKHome(@NotNull VirtualFile file) {
    final VirtualFile lib = file.findChild("lib");
    if (lib == null) {
      return false;
    }

    return isGradleSdk(lib.getChildren());
  }

  @Nullable
  public static VirtualFile getSdkHome(@Nullable Module module) {
    final VirtualFile gradleJar = findGradleJar(module);

    if (gradleJar != null) {
      final VirtualFile parent = gradleJar.getParent();
      if (parent != null && "lib".equals(parent.getName())) {
        return parent.getParent();
      }
    }
    return null;
  }

  @Nullable
  public static VirtualFile findGradleJar(@Nullable Module module) {
    if (module == null) {
      return null;
    }

    return findGradleJar(ModuleRootManager.getInstance(module).getFiles(OrderRootType.CLASSES));
  }

  @Nullable
  private static VirtualFile findGradleJar(VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (isGradleJar(file)) {
        return PathUtil.getLocalFile(file);
      }
    }
    return null;
  }

  public static boolean isGradleSdk(VirtualFile[] files) {
    return findGradleJar(files) != null;
  }

  private static boolean isGradleJar(VirtualFile file) {
    return GRADLE_JAR_FILE_PATTERN.matcher(file.getName()).matches();
  }

  @Override
  public boolean managesLibrary(@NotNull Library library, LibrariesContainer container) {
    return isGradleSdk(container.getLibraryFiles(library, OrderRootType.CLASSES));
  }

  @NotNull
  @Override
  public String getSDKVersion(String path) {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    for (VirtualFile virtualFile : file.findChild("lib").getChildren()) {
      final String version = getGradleJarVersion(virtualFile);
      if (version != null) {
        return version;
      }
    }
    throw new AssertionError(path);
  }

  @Nullable
  private static String getGradleJarVersion(VirtualFile file) {
    final Matcher matcher = GRADLE_JAR_FILE_PATTERN.matcher(file.getName());
    if (matcher.matches()) {
      return matcher.group(2);
    }
    return null;
  }

  @Nls
  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return "Gradle";
  }

  @Override
  protected void fillLibrary(String path, Library.ModifiableModel model) {
    File lib = new File(path + "/lib");
    File[] jars = lib.exists() ? lib.listFiles() : new File[0];
    if (jars != null) {
      for (File file : jars) {
        if (file.getName().endsWith(".jar")) {
          model.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
        }
      }
    }
  }

}
