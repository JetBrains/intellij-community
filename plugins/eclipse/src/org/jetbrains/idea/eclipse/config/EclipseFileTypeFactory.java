// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseXml;

public class EclipseFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(EclipseFileType.INSTANCE, EclipseXml.CLASSPATH_EXT + ";" + EclipseXml.PROJECT_EXT);
  }
}
