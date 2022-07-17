// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey Evdokimov
 */
public class GroovyMethodDescriptorExtension extends GroovyMethodDescriptor implements PluginAware {

  public static final ExtensionPointName<GroovyMethodDescriptorExtension> EP_NAME =
    new ExtensionPointName<>("org.intellij.groovy.methodDescriptor");

  @Attribute("class")
  public String className;

  @Attribute("lightMethodKey")
  public String lightMethodKey;

  private PluginDescriptor myPluginDescriptor;

  @Override
  public final void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public @NotNull ClassLoader getLoaderForClass() {
    return myPluginDescriptor != null ?
           myPluginDescriptor.getClassLoader() :
           getClass().getClassLoader();
  }
}
