// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ObjectUtils;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.ApiStatus;
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
  @Stubbed
  GenericAttributeValue<Boolean> getDynamic();

  @Attribute("hasAttributes")
  GenericAttributeValue<Boolean> getHasAttributes();

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
  @NotNull @NlsSafe
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
   * @see #getExtensionPointClassName()
   */
  @Nullable
  PsiClass getExtensionPointClass();

  /**
   * <em>NOTE</em> Inner class is separated via {@code '$'}
   *
   * @see #getExtensionPointClass()
   */
  @SuppressWarnings("unused")
  @Nullable
  String getExtensionPointClassName();

  /**
   * @return the EP DOM element that defines the instantiated extension class in implemented extensions.
   * Determining the element follows the algorithm described in {@link #getExtensionPointClass()}.
   */
  @Nullable
  DomElement getExtensionPointClassNameElement();

  /**
   * Returns EP name prefix (Plugin ID).
   *
   * @return {@code null} if {@code qualifiedName} is set.
   */
  @Nullable @NlsSafe
  String getNamePrefix();

  /**
   * Returns EP fields missing {@code <with>} declaration to specify type.
   *
   * @return Fields.
   */
  List<PsiField> collectMissingWithTags();

  /**
   * Returns status of EP for presentation/highlighting.
   */
  @NotNull
  ExtensionPoint.Status getExtensionPointStatus();

  interface Status {

    enum Kind {
      /**
       * Nothing to report.
       */
      DEFAULT,

      /**
       * Unresolved EP class (setup problem).
       */
      UNRESOLVED_CLASS,

      /**
       * Deprecated EP class.
       *
       * @see #SCHEDULED_FOR_REMOVAL_API
       */
      DEPRECATED,

      /**
       * Deprecated EP, replacement EP available via {@link #getAdditionalData()}.
       */
      ADDITIONAL_DEPRECATED,

      /**
       * Obsolete API, should not be used for new code.
       *
       * @see ApiStatus.Obsolete
       */
      OBSOLETE,

      /**
       * Internal API, should not be used outside of IntelliJ project.
       *
       * @see ApiStatus.Internal
       */
      INTERNAL_API,

      /**
       * Experimental API, might be removed or break in future versions.
       *
       * @see ApiStatus.Experimental
       */
      EXPERIMENTAL_API,

      /**
       * Scheduled for removal API via {@code @Deprecated(forRemoval=true)} or {@link ApiStatus.ScheduledForRemoval}.
       */
      SCHEDULED_FOR_REMOVAL_API
    }

    /**
     * @return Most "relevant" kind.
     */
    Kind getKind();

    /**
     * Provides additional data depending on {@link #getKind()}.
     *
     * @return <ul>
     * <li>{@link Kind#ADDITIONAL_DEPRECATED} - replacement EP or {@code null} if none defined</li>
     * <li>{@link Kind#SCHEDULED_FOR_REMOVAL_API} - {@link ApiStatus.ScheduledForRemoval} {@code inVersion} attribute value</li>
     * </ul>
     */
    @Nullable
    String getAdditionalData();
  }

  static @Nullable ExtensionPoint resolveFromDeclaration(PsiElement declaration) {
    DomElement domElement = null;
    if (declaration instanceof PomTargetPsiElement) {
      final PomTarget pomTarget = ((PomTargetPsiElement)declaration).getTarget();
      if (pomTarget instanceof DomTarget) {
        domElement = ((DomTarget)pomTarget).getDomElement();
      }
    } // via XmlTag for "qualifiedName"
    else if (declaration instanceof XmlTag) {
      domElement = DomUtil.getDomElement(declaration);
    }

    return ObjectUtils.tryCast(domElement, ExtensionPoint.class);
  }
}
