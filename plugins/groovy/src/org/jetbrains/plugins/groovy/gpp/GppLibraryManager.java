package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.config.AbstractGroovyLibraryManager;

import javax.swing.*;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class GppLibraryManager extends AbstractGroovyLibraryManager {
  private static final Pattern GROOVYPP_JAR = Pattern.compile("groovypp-([\\d\\.]+)\\.jar");
  private static final Pattern GROOVYPP_ALL_JAR = Pattern.compile("groovypp-all-([\\d\\.]+)\\.jar");
  
  @Override
  protected void fillLibrary(String path, Library.ModifiableModel model) {
    File lib = new File(path + "/lib");
    if (lib.exists()) {
      model.addJarDirectory(VfsUtil.getUrlForLibraryRoot(lib), false);
    }

    File srcRoot = new File(path + "/src");
    addSources(model, srcRoot.exists() ? srcRoot : new File(path));
  }

  private static void addSources(Library.ModifiableModel model, File srcRoot) {
    File compilerSrc = new File(srcRoot, "Compiler/src");
    if (compilerSrc.exists()) {
      model.addRoot(VfsUtil.getUrlForLibraryRoot(compilerSrc), OrderRootType.SOURCES);
    }

    File stdLibSrc = new File(srcRoot, "StdLib/src");
    if (stdLibSrc.exists()) {
      model.addRoot(VfsUtil.getUrlForLibraryRoot(stdLibSrc), OrderRootType.SOURCES);
    }

    File mainSrc = new File(srcRoot, "main");
    if (mainSrc.exists()) {
      model.addRoot(VfsUtil.getUrlForLibraryRoot(mainSrc), OrderRootType.SOURCES);
    }
  }

  @Override
  public boolean managesLibrary(@NotNull Library library, LibrariesContainer container) {
    return getGppVersion(container.getLibraryFiles(library, OrderRootType.CLASSES)) != null;
  }

  @Nls
  @Override
  public String getLibraryVersion(@NotNull Library library, LibrariesContainer librariesContainer) {
    return getGppVersion(librariesContainer.getLibraryFiles(library, OrderRootType.CLASSES));
  }

  @Nullable
  private static String getGppVersion(VirtualFile[] files) {
    for (VirtualFile file : files) {
      Matcher matcher = GROOVYPP_JAR.matcher(file.getName());
      if (matcher.matches()) {
        return matcher.group(1);
      }

      matcher = GROOVYPP_ALL_JAR.matcher(file.getName());
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return GroovyIcons.GROOVY_ICON_16x16;
  }

  @NotNull
  @Override
  public String getSDKVersion(String path) {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    assert file != null;
    final VirtualFile libDir = file.findChild("lib");
    assert libDir != null;
    final String version = getGppVersion(libDir.getChildren());
    if (version != null) {
      return version;
    }
    throw new AssertionError(path);
  }

  @NotNull
  @Override
  public String getAddActionText() {
    return "Create new Groovy++ SDK...";
  }


  @Nls
  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return "Groovy++";
  }

  public boolean managesName(@NotNull String name) {
    return super.managesName(name) || StringUtil.startsWithIgnoreCase(name, "groovypp");
  }

  @Override
  public boolean isSDKHome(@NotNull VirtualFile file) {
    final VirtualFile libDir = file.findChild("lib");
    return libDir != null && getGppVersion(libDir.getChildren()) != null;
  }
}
