package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.action.EclipseBundle;

import javax.swing.*;

/**
 * Author: Vladislav.Kaznacheev
 */
public class EclipseFileType implements FileType {
  public static final Icon eclipseIcon = IconLoader.getIcon("/images/eclipse.gif");


  @NotNull
  @NonNls
  public String getName() {
    return "Eclipse";
  }

  @NotNull
  public String getDescription() {
    return EclipseBundle.message("eclipse.file.type.descr");
  }

  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return EclipseXml.CLASSPATH_EXT;
  }

  @Nullable
  public Icon getIcon() {
    return eclipseIcon;
  }

  public boolean isBinary() {
    return false;
  }

  public boolean isReadOnly() {
    return false;
  }

  public String getCharset(@NotNull final VirtualFile file) {
    return CharsetToolkit.UTF8;
  }
}
