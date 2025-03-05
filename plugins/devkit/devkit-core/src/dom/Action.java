// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupPresentationProvider;
import org.jetbrains.idea.devkit.dom.impl.KeymapConverter;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;
import org.jetbrains.idea.devkit.dom.keymap.KeymapXmlRootElement;

import java.util.List;

@Presentation(typeName = DevkitDomPresentationConstants.ACTION, provider = ActionOrGroupPresentationProvider.class)
public interface Action extends ActionOrGroup {

  @Override
  @Nullable
  default String getEffectiveId() {
    String id = ActionOrGroup.super.getEffectiveId();
    if (id != null) return id;

    String clazzValue = getClazz().getStringValue();
    return clazzValue != null ? StringUtilRt.getShortName(clazzValue) : null;
  }

  @Override
  default GenericAttributeValue<?> getEffectiveIdAttribute() {
    if (DomUtil.hasXml(getId())) {
      return getId();
    }

    return getClazz();
  }

  @NotNull
  @Attribute("class")
  @Required
  @ExtendClass(value = "com.intellij.openapi.actionSystem.AnAction",
    allowNonPublic = true, allowAbstract = false, allowInterface = false)
  @Convert(PluginPsiClassConverter.class)
  GenericAttributeValue<PsiClass> getClazz();


  @NotNull
  List<KeyboardShortcut> getKeyboardShortcuts();

  KeyboardShortcut addKeyboardShortcut();


  @NotNull
  List<MouseShortcut> getMouseShortcuts();

  MouseShortcut addMouseShortcut();


  @NotNull
  List<Abbreviation> getAbbreviations();

  Abbreviation addAbbreviation();


  @NotNull
  List<AddToGroup> getAddToGroups();

  AddToGroup addAddToGroup();

  @NotNull
  List<Synonym> getSynonyms();

  Synonym addSynonym();

  @NotNull
  @Convert(KeymapConverter.class)
  GenericAttributeValue<KeymapXmlRootElement> getKeymap();

  /**
   * @see com.intellij.openapi.project.ProjectTypeService
   */
  @NotNull
  GenericAttributeValue<String> getProjectType();
}
