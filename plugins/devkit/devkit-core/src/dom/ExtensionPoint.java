// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

@Presentation(typeName = DevkitDomPresentationConstants.EXTENSION_POINT)
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
   * Use {@link #getEffectiveQualifiedName()} for presentation.
   */
  @NotNull
  @Stubbed
  @NameValue
  GenericAttributeValue<String> getName();

  /**
   * Use {@link #getEffectiveQualifiedName()} for presentation.
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
   * Returns the extension point class.
   *
   * @return {@link #getInterface()} if defined, {@link #getBeanClass()} otherwise.
   */
  @Nullable
  PsiClass getEffectiveClass();

  /**
   * Returns the actual EP class to implement/override.
   *
   * @return Determined in the following order:
   * <ol>
   *   <li>{@link #getInterface()} if defined</li>
   *   <li>first {@code <with> "implements"} class if exactly one {@code <with>} element defined</li>
   *   <li>first {@code <with> "implements"} class where attribute name matching common naming rules ({@code "implementationClass"} etc.)</li>
   *   <li>{@code null} if none of above rules apply</li>
   * </ol>
   */
  @Nullable
  PsiClass getExtensionPointClass();

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
