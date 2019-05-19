// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;

import java.util.List;

@Presentation(typeName = "Extension Point")
public interface ExtensionPoint extends DomElement {
  enum Area {
    IDEA_PROJECT,
    IDEA_MODULE,
    IDEA_APPLICATION
  }

  @NotNull
  @Override
  XmlTag getXmlTag();

  @NotNull
  @Stubbed
  @NameValue
  GenericAttributeValue<String> getName();

  @Attribute("qualifiedName")
  @Stubbed
  GenericAttributeValue<String> getQualifiedName();

  @NotNull
  @Stubbed
  @Convert(PluginPsiClassConverter.class)
  GenericAttributeValue<PsiClass> getInterface();

  @NotNull
  @Stubbed
  @Attribute("beanClass")
  @Convert(PluginPsiClassConverter.class)
  GenericAttributeValue<PsiClass> getBeanClass();

  @NotNull
  GenericAttributeValue<Area> getArea();

  @NotNull
  @Stubbed
  @SubTagList("with")
  List<With> getWithElements();

  With addWith();

  /**
   * Returns the fully qualified EP name
   *
   * @return {@code PluginID.name} or {@code qualifiedName}.
   */
  @NotNull
  String getEffectiveQualifiedName();

  /**
   * Returns the actually defined name.
   *
   * @return {@link #getName()} if defined, {@link #getQualifiedName()} otherwise.
   */
  @NotNull
  String getEffectiveName();

  /**
   * Returns EP name prefix (Plugin ID).
   *
   * @return {@code null} if {@code qualifiedName} is set.
   */
  @Nullable
  String getNamePrefix();

  /**
   * Returns EP fields missing {@code <with>} declaration to specify type.
   *
   * @return Fields.
   */
  List<PsiField> collectMissingWithTags();
}
