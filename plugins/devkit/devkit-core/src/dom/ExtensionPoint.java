// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.ApiStatus;
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

  /**
   * @see #getEffectiveName()
   */
  @NotNull
  @Stubbed
  @NameValue
  GenericAttributeValue<String> getName();

  /**
   * @see #getEffectiveName()
   */
  @Attribute("qualifiedName")
  @Stubbed
  GenericAttributeValue<String> getQualifiedName();

  /**
   * @see #getEffectiveClass()
   */
  @NotNull
  @Stubbed
  @Convert(PluginPsiClassConverter.class)
  GenericAttributeValue<PsiClass> getInterface();

  /**
   * @see #getEffectiveClass()
   */
  @NotNull
  @Stubbed
  @Attribute("beanClass")
  @Convert(PluginPsiClassConverter.class)
  GenericAttributeValue<PsiClass> getBeanClass();

  @NotNull
  GenericAttributeValue<Area> getArea();

  @NotNull
  @Attribute("dynamic")
  @ApiStatus.Experimental
  GenericAttributeValue<Boolean> getDynamic();

  @NotNull
  @Stubbed
  @SubTagList("with")
  List<With> getWithElements();

  With addWith();

  /**
   * Returns the fully qualified EP name.
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
   * Returns the extension point class.
   *
   * @return {@link #getInterface()} if defined, {@link #getBeanClass()} otherwise.
   */
  @Nullable
  PsiClass getEffectiveClass();

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
