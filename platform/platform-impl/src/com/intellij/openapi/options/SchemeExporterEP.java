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
package com.intellij.openapi.options;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Extension point for schemes exporters.
 * 
 * @author Rustam Vishnyakov
 */
public class SchemeExporterEP <S extends Scheme> extends AbstractExtensionPointBean {
  public static final ExtensionPointName<SchemeExporterEP> EP_NAME = ExtensionPointName.create("com.intellij.schemeExporter");
  
  @Attribute("name")
  public String name;

  @Attribute("schemeClass")
  public String schemeClass;

  @Attribute("implementationClass")
  public String implementationClass;
  
  private final LazyInstance<SchemeExporter<S>> myExporterInstance = new LazyInstance<SchemeExporter<S>>() {
    @Override
    protected Class<SchemeExporter<S>> getInstanceClass() throws ClassNotFoundException {
      return findClass(implementationClass);
    }
  };
  
  public SchemeExporter<S> getInstance() {
    return myExporterInstance.getValue();
  }
  
  /**
   * Finds extensions supporting the given {@code schemeClass}
   * @param schemeClass The class of the scheme to search extensions for.
   * @return A collection of exporters capable of exporting schemes of the given class. An empty collection is returned if there are
   *         no matching exporters.
   */
  @NotNull
  public static <S extends Scheme> Collection<SchemeExporterEP<S>> getExtensions(Class<S> schemeClass) {
    List<SchemeExporterEP<S>> exporters = new ArrayList<>();
    for (SchemeExporterEP<?> exporterEP : EP_NAME.getExtensions()) {
      if (schemeClass.getName().equals(exporterEP.schemeClass)) {
        //noinspection unchecked
        exporters.add((SchemeExporterEP<S>)exporterEP);
      }
    }
    return exporters;
  }

  /**
   * Find an exporter for the given name and scheme class. It is allowed for exporters to have the same name but different scheme classes.
   * @param name        The exporter name as defined in plug-in configuration.
   * @param schemeClass The scheme class the exporter has to support.
   * @return The found exporter or null if there are no exporters for the given name and scheme class.
   */
  @Nullable
  public static <S extends Scheme> SchemeExporter<S> getExporter(@NotNull String name, Class<S> schemeClass) {
    for (SchemeExporterEP<S> exporterEP : getExtensions(schemeClass)) {
      if (name.equals(exporterEP.name)) {
        return exporterEP.getInstance();
      }
    }
    return null;
  }
}
