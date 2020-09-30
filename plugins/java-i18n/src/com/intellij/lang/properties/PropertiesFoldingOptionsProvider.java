// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.application.options.editor.CodeFoldingOptionsProvider;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.BeanConfigurable;

@State(name = "PropertiesFoldingSettings", storages = @Storage("editor.xml"))
public class PropertiesFoldingOptionsProvider extends BeanConfigurable<PropertiesFoldingSettings> implements CodeFoldingOptionsProvider {

  public PropertiesFoldingOptionsProvider() {
    super(PropertiesFoldingSettings.getInstance(), PropertiesFileType.INSTANCE.getDescription());
    PropertiesFoldingSettings settings = getInstance();

    checkBox(JavaI18nBundle.message("checkbox.fold.to.context"), settings::isFoldPlaceholdersToContext, settings::setFoldPlaceholdersToContext);
  }
}