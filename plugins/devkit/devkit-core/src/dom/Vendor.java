// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

@Presentation(icon = "AllIcons.General.User")
public interface Vendor extends GenericDomValue<String> {

  @Override
  @NotNull
  @NoSpellchecking
  @Required
  String getValue();

  @NotNull
  @NoSpellchecking
  GenericAttributeValue<String> getEmail();


  @NotNull
  @NoSpellchecking
  GenericAttributeValue<String> getUrl();


  /**
   * @deprecated not used anymore
   */
  @NotNull
  @NoSpellchecking
  @Deprecated
  GenericAttributeValue<String> getLogo();
}
