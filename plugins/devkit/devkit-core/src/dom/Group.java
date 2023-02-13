// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupPresentationProvider;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;

import java.util.List;

@Presentation(typeName = DevkitDomPresentationConstants.GROUP, provider = ActionOrGroupPresentationProvider.class)
@Stubbed
public interface Group extends ActionContainer, ActionOrGroup {

  @NotNull
  GenericAttributeValue<Boolean> getCompact();

  @NotNull
  GenericAttributeValue<Boolean> getSearchable();

  @NotNull
  @Attribute("class")
  @ExtendClass(value = "com.intellij.openapi.actionSystem.ActionGroup",
    allowNonPublic = true, allowAbstract = false, allowInterface = false)
  @Convert(PluginPsiClassConverter.class)
  GenericAttributeValue<PsiClass> getClazz();


  @NotNull
  List<Separator> getSeparators();

  Separator addSeparator();


  @NotNull
  List<AddToGroup> getAddToGroups();

  AddToGroup addAddToGroup();
}
