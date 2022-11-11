// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.structuralsearch.Scopes;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Bas Leijdekkers
 */
@Service(Service.Level.APP)
@State(name = "ui-state", storages = @Storage("structuralSearch.xml"), category = SettingsCategory.UI)
final class UIState implements PersistentStateComponent<UIState> {

  public boolean migrated = false;
  /*@OptionTag(converter = TreeStateConverter.class)*/ public TreeState templatesTreeState;
  public Scopes.Type scopeType;
  public String scopeDescriptor;
  public boolean searchInjectedCode = true;
  public boolean matchCase = false;
  public boolean shortenFQNames = true;
  public boolean useStaticImport = false;
  public boolean reformat = true;
  public boolean filtersVisible = true;
  public boolean existingTemplatesVisible = true;
  public boolean pinned = false;

  public static UIState getInstance() {
    return ApplicationManager.getApplication().getService(UIState.class);
  }

  @Override
  public UIState getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull UIState state) {
    XmlSerializerUtil.copyBean(state, this);
    if (!migrated) {
      final PropertiesComponent properties = PropertiesComponent.getInstance();
      filtersVisible = properties.getBoolean("structural.search.filters.visible", true);
      existingTemplatesVisible = properties.getBoolean("structural.search.templates.visible", true);
      pinned = properties.getBoolean("structural.seach.pinned", false);
      properties.unsetValue("structural.search.filters.visible");
      properties.unsetValue("structural.search.templates.visible");
      properties.unsetValue("structural.seach.pinned");
      properties.unsetValue("structural.search.shorten.fqn");
      properties.unsetValue("structural.search.reformat");
      properties.unsetValue("structural.search.use.static.import");
      migrated = true;
    }
  }
}
class TreeStateConverter extends Converter<TreeState> {
  @Override
  public @Nullable TreeState fromString(@NotNull String value) {
    try {
      final Element element = JDOMUtil.load(value);
      return TreeState.createFrom(element);
    }
    catch (IOException e) {
      return null;
    }
    catch (JDOMException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public @Nullable String toString(@NotNull TreeState value) {
    final Element element = new Element("treeState");
    value.writeExternal(element);
    return JDOMUtil.writeElement(element);
  }
}