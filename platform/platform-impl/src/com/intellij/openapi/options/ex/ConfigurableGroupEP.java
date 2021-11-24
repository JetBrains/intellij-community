// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ResourceBundle;

/**
 * This extension point is intended to group different configurables in order to simplify finding them in the Settings tree.
 * If a group in the tree is selected, an uneditable page with a brief description is displayed.
 * Therefore, do not use groups to create a hierarchy of configurables, especially if there are very few of them.
 * It is recommended to create a hierarchy using {@link com.intellij.openapi.options.ConfigurableEP#parentId ConfigurableEP}.
 */
final class ConfigurableGroupEP implements PluginAware {
  private static final ExtensionPointName<ConfigurableGroupEP> EP = new ExtensionPointName<>("com.intellij.groupConfigurable");

  /**
   * This attribute specifies the unique identifier of the configurable group.
   * It can be referenced from the {@link #parentId} or from the Configurable class.
   *
   * @see com.intellij.openapi.options.ConfigurableEP#parentId
   * @see com.intellij.openapi.options.ConfigurableEP#groupId
   */
  @NonNls
  @RequiredElement
  @Attribute("id")
  public String id;

  /**
   * This attribute is used to create a hierarchy of settings.
   * If it is set, the configurable group will be a child of the specified parent.
   *
   * @see com.intellij.openapi.options.ConfigurableEP#parentId
   * @see com.intellij.openapi.options.ConfigurableEP#groupId
   */
  @NonNls
  @Attribute("parentId")
  public String parentId;

  /**
   * This attribute specifies the weight of the configurable group within a parent group.
   *
   * @see com.intellij.openapi.options.ConfigurableEP#groupWeight
   */
  @Attribute("weight")
  public int weight;

  /**
   * This attribute specifies the topic in the help file.
   *
   * @see com.intellij.openapi.options.Configurable#getHelpTopic
   */
  @NonNls
  @Attribute("helpTopic")
  public String helpTopic;

  /**
   * This attribute specifies the resource bundle that contains display name and description.
   * If it is not set the plugin-specific bundle will be used.
   * If it does not contain needed value, the {@link OptionsBundle} will be used.
   */
  @Attribute("bundle")
  public String bundle;

  /**
   * This attribute specifies the key to retrieve a display name from the given {@link #bundle}.
   *
   * @see com.intellij.openapi.options.Configurable#getDisplayName
   */
  @RequiredElement
  @Attribute("displayNameKey")
  @NlsContexts.ConfigurableName
  public String displayNameKey;

  /**
   * This attribute specifies the key to retrieve a description from the given {@link #bundle}.
   * Note that it should be HTML-based text to layout a long text in a proper way.
   */
  @RequiredElement
  @Attribute("descriptionKey")
  @NlsContexts.DetailedDescription
  public String descriptionKey;

  private PluginDescriptor myPluginDescriptor;

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor descriptor) {
    myPluginDescriptor = descriptor;
  }

  @NotNull @NlsContexts.ConfigurableName String getDisplayName() {
    return getResourceValue(displayNameKey);
  }

  @NlsContexts.DetailedDescription @NotNull String getDescription() {
    return getResourceValue(descriptionKey);
  }

  @NotNull ResourceBundle getResourceBundle() {
    PluginDescriptor descriptor = myPluginDescriptor;
    String pathToBundle = bundle;
    if (pathToBundle == null) {
      if (descriptor != null) pathToBundle = descriptor.getResourceBundleBaseName();
      if (pathToBundle == null) return OptionsBundle.INSTANCE.getResourceBundle();
    }
    ClassLoader classLoader = descriptor != null ? descriptor.getPluginClassLoader() : null;
    return DynamicBundle.INSTANCE.getResourceBundle(pathToBundle,
                                                    classLoader != null ? classLoader : getClass().getClassLoader());
  }

  @NotNull @NlsContexts.ConfigurableName String getResourceValue(@NotNull String key) {
    String message = AbstractBundle.messageOrNull(getResourceBundle(), key);
    return message != null ? message : OptionsBundle.message(key);
  }

  static @Nullable ConfigurableGroupEP find(@NotNull String id) {
    return EP.findFirstSafe(ep -> id.equals(ep.id));
  }
}
