package org.jetbrains.idea.eclipse.config;

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.action.EclipseBundle;

import javax.swing.*;

/**
 * Author: Vladislav.Kaznacheev
 */
public class EclipseFileType extends LanguageFileType {
  public static final Icon eclipseIcon = IconLoader.getIcon("/images/eclipse.gif");

  public EclipseFileType() {
    super(StdLanguages.XML);
  }

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
}
