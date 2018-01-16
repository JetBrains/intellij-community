/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.eclipse.importer;

import com.intellij.openapi.options.SchemeImportException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class EclipseCodeStylePropertiesImporter extends EclipseFormatterOptionsHandler {

  public final static String CODE_STYLE_PROPERTY_FILE = "org.eclipse.jdt.core.prefs";

  public void importProperties(@NotNull InputStream inputStream, @NotNull CodeStyleSettings settings) throws IOException,
                                                                                                             SchemeImportException {
    Properties properties = new Properties();
    properties.load(inputStream);
    for (String key : properties.stringPropertyNames()) {
      String value = properties.getProperty(key);
      if (value != null) {
        setCodeStyleOption(settings, key, value);
      }
    }
  }
}
