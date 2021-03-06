// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupPresentationProvider;
import org.jetbrains.idea.devkit.dom.impl.KeymapConverter;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;

import java.util.List;

@Presentation(typeName = DevkitDomPresentationConstants.ACTION, provider = ActionOrGroupPresentationProvider.class)
public interface Action extends ActionOrGroup {

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
  GenericAttributeValue<XmlFile> getKeymap();

  @NotNull
  GenericAttributeValue<String> getProjectType();
}
