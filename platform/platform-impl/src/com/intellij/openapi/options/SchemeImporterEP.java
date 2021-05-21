/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Extension point for scheme importers. Example of definition in {@code plugin.xml}:
 * <pre>
 * &lt;schemeImporter
 *         nameBundle="messages.PluginBundle"
 *         nameKey="bundle.name.key"
 *         schemeClass="com.intellij.psi.codeStyle.CodeStyleScheme"
 *         implementationClass="org.acme.ImporterClass"&gt;
 * </pre>
 * {@code ImporterClass} must extend {@link SchemeImporter}
 */
public final class SchemeImporterEP <S extends Scheme> extends SchemeConvertorEPBase<SchemeImporter<S>> {
  public static final ExtensionPointName<SchemeImporterEP<?>> EP_NAME = ExtensionPointName.create("com.intellij.schemeImporter");

  @Attribute("schemeClass")
  public String schemeClass;

  /**
   * Finds extensions supporting the given {@code schemeClass}
   * @param schemeClass The class of the scheme to search extensions for.
   * @return A collection of importers capable of importing schemes of the given class. An empty collection is returned if there are
   *         no matching importers.
   */
  @NotNull
  public static <S extends Scheme> Collection<SchemeImporterEP<S>> getExtensions(Class<S> schemeClass) {
    List<SchemeImporterEP<S>> importers = new ArrayList<>();
    for (SchemeImporterEP<?> importerEP : EP_NAME.getExtensions()) {
      if (schemeClass.getName().equals(importerEP.schemeClass)) {
        //noinspection unchecked
        importers.add((SchemeImporterEP<S>)importerEP);
      }
    }
    return importers;
  }


  /**
   * Find an importer for the given name and scheme class. It is allowed for importers to have the same name but different scheme classes.
   * @param name        The importer name as defined in plug-in configuration.
   * @param schemeClass The scheme class the importer has to support.
   * @return The found importer or null if there are no importers for the given name and scheme class.
   */
  @Nullable
  public static <S extends Scheme> SchemeImporter<S> getImporter(@NotNull String name, Class<S> schemeClass) {
    for (SchemeImporterEP<S> importerEP : getExtensions(schemeClass)) {
      if (name.equals(importerEP.getLocalizedName())) {
        return importerEP.getInstance();
      }
    }
    return null;
  }

}
