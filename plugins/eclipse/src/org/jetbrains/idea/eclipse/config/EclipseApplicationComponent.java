package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseXml;

/**
 * Author: Vladislav.Kaznacheev
 */
public class EclipseApplicationComponent implements ApplicationComponent {

  private final FileType fileType = new EclipseFileType();

  @NonNls
  @NotNull
  public String getComponentName() {
    return "EclipseApplicationComponent";
  }

  public void initComponent() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileTypeManagerEx.getInstance().registerFileType(fileType, EclipseXml.CLASSPATH_EXT, EclipseXml.PROJECT_EXT);
      }
    });
  }

  public void disposeComponent() {
  }
}
