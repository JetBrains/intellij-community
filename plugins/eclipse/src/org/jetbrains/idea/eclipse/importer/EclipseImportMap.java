/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Properties;

/**
 * @author Rustam Vishnyakov
 */
public class EclipseImportMap {
  private Properties myProperties;
  private final static String MAP_PROPERTIES = "EclipseImportMap.properties";
  private static final Logger LOG = Logger.getInstance(EclipseImportMap.class);

  public EclipseImportMap() {
    myProperties = new Properties();    
  }

  public void load() {
    try {
      InputStream sourceStream = getClass().getResourceAsStream(MAP_PROPERTIES);
      try {
        myProperties.load(sourceStream);
      }
      finally {
        sourceStream.close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
  
  @Nullable
  public ImportDescriptor getImportDescriptor(String name) {
    String rawData = myProperties.getProperty(name);
    if (rawData != null && !rawData.trim().isEmpty()) {
      if (rawData.contains(":")) {
        String[] parameters = rawData.split(":");
        if (parameters.length == 2) {
          return new ImportDescriptor(parameters[0].trim(), parameters[1].trim());
        }
        else if (parameters.length == 3) {
          boolean indentOptions = "indentOptions".equalsIgnoreCase(parameters[1].trim());
          return new ImportDescriptor(parameters[0].trim(), parameters[2], indentOptions);
        }
      }
      else {
        return new ImportDescriptor(rawData.trim());
      }
    }
    return null;
  }
  
  public static class ImportDescriptor {
    private String myLanguage;
    private String myFieldName;
    private boolean myIndentOptions;
    private boolean myIsCustomField;

    public ImportDescriptor(String language, String fieldName, boolean indentOptions) {
      myLanguage = language;
      myFieldName = fieldName;
      myIsCustomField = isCustomField(fieldName);
      myIndentOptions = indentOptions;
    }
    
    public ImportDescriptor(String language, String fieldName) {
      this(language, fieldName, false);
    }
    
    public ImportDescriptor(String fieldName) {
      this(null, fieldName);
    }

    public String getLanguage() {
      return myLanguage;
    }

    public String getFieldName() {
      return myFieldName;
    }

    public boolean isIndentOptions() {
      return myIndentOptions;
    }
    
    public boolean isLanguageSpecific() {
      return myLanguage != null;
    }

    public boolean isCustomField() {
      return myIsCustomField;
    }

    private static boolean isCustomField(@NotNull String fieldName) {
      if (EclipseCodeStyleImportWorker.PROGRAMMATIC_IMPORT_KEY.equals(fieldName)) {
        return false;
      }
      for (Field field : CommonCodeStyleSettings.class.getFields()) {
        if (fieldName.equals(field.getName())) return false;
      }
      for (Field field : CommonCodeStyleSettings.IndentOptions.class.getFields()) {
        if (fieldName.equals(field.getName())) return false;
      }
      return true;
    }
  }
}
