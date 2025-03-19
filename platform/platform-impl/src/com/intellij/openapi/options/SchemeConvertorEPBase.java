// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ResourceBundle;

public class SchemeConvertorEPBase<T> extends BaseKeyedLazyInstance<T> {
  private static final Logger LOG = Logger.getInstance(SchemeExporterEP.class);

  /**
   * Use {@link #nameKey} for I18N.
   *
   * @see #getLocalizedName()
   */
  @Attribute("name") public @Nls(capitalization = Nls.Capitalization.Sentence) String name;

  @Attribute("nameKey") public @Nls(capitalization = Nls.Capitalization.Sentence) String nameKey;

  @Attribute("nameBundle")
  public String nameBundle;

  @Attribute("implementationClass")
  public String implementationClass;

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }

  /**
   * @return A localized exporter/importer name to be used in UI if <i>nameBundle</i> <b>and</b> <i>nameKey</i> attributes are specified.
   * Otherwise the <i>name</i> attribute as is. Reports an error if the attributes are missing.
   */
  public @NotNull String getLocalizedName() {
    if (nameBundle != null && nameKey != null) {
      ResourceBundle resourceBundle = DynamicBundle.getResourceBundle(getPluginDescriptor().getClassLoader(), nameBundle);
      return BundleBase.messageOrDefault(resourceBundle, nameKey, null);
    }
    else if (name == null) {
      LOG.error("Either a pair ('nameBundle', 'nameKey') or 'name' attribute must be specified.");
    }
    return name;
  }
}
