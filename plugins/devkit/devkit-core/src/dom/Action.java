/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupResolveConverter;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;

import java.util.List;

@Presentation(typeName = "Action")
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
  @Convert(ActionOrGroupResolveConverter.OnlyActions.class)
  GenericAttributeValue<ActionOrGroup> getUseShortcutOf();

  @NotNull
  GenericAttributeValue<String> getKeymap();

  @NotNull
  GenericAttributeValue<String> getProjectType();
}
