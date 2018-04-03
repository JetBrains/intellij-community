/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.eclipse.importer;

import com.intellij.openapi.options.SchemeImportException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;

public class EclipseCodeStylePropertiesImporter extends EclipseFormatterOptionsHandler {

  public void importProperties(@NotNull Properties eclipseProperties, @NotNull CodeStyleSettings settings) throws SchemeImportException {
    for (String key : eclipseProperties.stringPropertyNames()) {
      String value = eclipseProperties.getProperty(key);
      if (value != null) {
        setCodeStyleOption(settings, key, value);
      }
    }
  }

}
