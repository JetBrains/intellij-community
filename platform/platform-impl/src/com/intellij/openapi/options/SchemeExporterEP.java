// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Extension point for schemes exporters. Example of definition in {@code plugin.xml}:
 * <pre>
 * &lt;schemeExporter
 *         nameBundle="messages.PluginBundle"
 *         nameKey="bundle.name.key"
 *         schemeClass="com.intellij.psi.codeStyle.CodeStyleScheme"
 *         implementationClass="org.acme.ExporterClass"&gt;
 * </pre>
 * {@code ExporterClass} must extend {@link SchemeExporter}
 */
public final class SchemeExporterEP<S extends Scheme> extends SchemeConvertorEPBase<SchemeExporter<S>> {
  public static final ExtensionPointName<SchemeExporterEP<?>> EP_NAME = ExtensionPointName.create("com.intellij.schemeExporter");

  @Attribute("schemeClass")
  public String schemeClass;

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
      if (name.equals(exporterEP.getLocalizedName())) {
        return exporterEP.getInstance();
      }
    }
    return null;
  }

}
