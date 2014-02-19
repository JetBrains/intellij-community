package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

/**
 * Created by Max Medvedev on 19/02/14
 */
public class EmbeddableGroovyLibraryPresentationProvider extends GroovyLibraryPresentationProviderBase {
  public static final LibraryKind GROOVY_ALL_KIND = LibraryKind.create("groovy-all");

  public EmbeddableGroovyLibraryPresentationProvider() {
    super(GROOVY_ALL_KIND);
  }

  @Override
  protected void fillLibrary(String path, LibraryEditor libraryEditor) {
    String trimmedName = StringUtil.trimEnd(path, "!/");
    File jarFile = new File(trimmedName);
    libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(jarFile), OrderRootType.CLASSES);
  }

  @Override
  public boolean managesLibrary(VirtualFile[] libraryFiles) {
    boolean hasGroovyAll = false;
    for (VirtualFile file : libraryFiles) {
      if ("jar".equals(file.getExtension())) {
        if (GroovyConfigUtils.getInstance().isGroovyAll(file)) {
          hasGroovyAll = true;
        }
        else {
          return false;
        }
      }
    }
    return hasGroovyAll;
  }

  @Override
  public String getLibraryVersion(VirtualFile[] libraryFiles) {
    for (VirtualFile file : libraryFiles) {
      String path = file.getCanonicalPath();
      if (path != null) {
        String version = AbstractConfigUtils.getSDKJarVersion(new File(StringUtil.trimEnd(path, "!/")), GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN, AbstractConfigUtils.MANIFEST_PATH);
        if (version != null) {
          return version;
        }
      }
    }

    return null;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  public boolean isSDKHome(@NotNull VirtualFile file) {
    return GroovyConfigUtils.getInstance().isGroovyAll(file);
  }

  @NotNull
  @Override
  public String getSDKVersion(String path) {
    return GroovyConfigUtils.getInstance().getSDKVersion(path);
  }

  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return "Groovy-All";
  }
}
