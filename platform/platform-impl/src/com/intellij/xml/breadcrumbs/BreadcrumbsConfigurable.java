// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.ide.ui.UISettings;
import com.intellij.lang.Language;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.intellij.openapi.application.ApplicationBundle.message;

final class BreadcrumbsConfigurable extends CompositeConfigurable<BreadcrumbsConfigurable.BreadcrumbsProviderConfigurable> implements SearchableConfigurable {

  private DialogPanel panel;

  @NotNull
  @Override
  public String getId() {
    return "editor.breadcrumbs";
  }

  @Override
  public String getDisplayName() {
    return message("configurable.breadcrumbs");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.editor.general.breadcrumbs";
  }

  @Override
  public JComponent createComponent() {
    if (panel == null) {
      panel = new BreadcrumbsConfigurableUI(getConfigurables()).panel;
    }
    return panel;
  }

  @Override
  public void reset() {
    panel.reset();
  }

  @Override
  public boolean isModified() {
    return panel.isModified();
  }

  @NotNull
  @Override
  protected List<BreadcrumbsProviderConfigurable> createConfigurables() {
    final List<BreadcrumbsProviderConfigurable> configurables = new SmartList<>();
    for (final BreadcrumbsProvider provider : BreadcrumbsProvider.EP_NAME.getExtensionList()) {
      for (final Language language : provider.getLanguages()) {
        configurables.add(new BreadcrumbsProviderConfigurable(provider, language));
      }
    }
    return configurables;
  }

  @Override
  public void apply() {
    boolean modified = panel.isModified();
    panel.apply();
    if (modified) UISettings.getInstance().fireUISettingsChanged();
  }

  static final class BreadcrumbsProviderConfigurable implements SearchableConfigurable {

    private final BreadcrumbsProvider myProvider;
    private final Language myLanguage;

    private BreadcrumbsProviderConfigurable(@NotNull final BreadcrumbsProvider provider, @NotNull final Language language) {
      myProvider = provider;
      myLanguage = language;
    }

    @Override
    public @NotNull JCheckBox createComponent() {
      return new JCheckBox(myLanguage.getDisplayName());
    }

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public void apply() throws ConfigurationException {
    }

    @NotNull
    @Override
    public String getId() {
      return myLanguage.getID();
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
      return myLanguage.getDisplayName();
    }

    @NotNull
    @Override
    public Class<?> getOriginalClass() {
      return myProvider.getClass();
    }
  }
}
