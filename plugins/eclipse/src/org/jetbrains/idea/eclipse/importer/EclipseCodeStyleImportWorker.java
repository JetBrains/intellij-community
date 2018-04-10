/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.eclipse.importer;

import com.intellij.openapi.options.SchemeImportException;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author Irina.Chernushina on 4/21/2015.
 */
public class EclipseCodeStyleImportWorker
  extends EclipseFormatterOptionsHandler
  implements EclipseXmlProfileElements, EclipseFormatterOptions {

  public void importScheme(@NotNull InputStream inputStream, final @Nullable String sourceScheme, final CodeStyleScheme scheme)
    throws SchemeImportException {
    final CodeStyleSettings settings = scheme.getCodeStyleSettings();
    EclipseXmlProfileReader reader = new EclipseXmlProfileReader(new EclipseXmlProfileReader.OptionHandler() {
      private String myCurrScheme;

      @Override
      public void handleOption(@NotNull String eclipseKey, @NotNull String value) throws SchemeImportException {
        if (sourceScheme == null || myCurrScheme != null && myCurrScheme.equals(sourceScheme)) {
          setCodeStyleOption(settings, eclipseKey, value);
        }
      }
      @Override
      public void handleName(String name) {
        myCurrScheme = name;
      }
    });
    reader.readSettings(inputStream);
  }

}
