package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseXml;

/**
 * Author: Vladislav.Kaznacheev
 */
public class EclipseFileTypeFactory extends FileTypeFactory {
  private final static FileType fileType = new EclipseFileType();

  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(fileType, EclipseXml.CLASSPATH_EXT + ";" + EclipseXml.PROJECT_EXT);
  }
}
