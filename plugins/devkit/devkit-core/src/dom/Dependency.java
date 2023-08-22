// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.paths.PathReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Stubbed;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.impl.DependencyConfigFileConverter;
import org.jetbrains.idea.devkit.dom.impl.IdeaPluginConverter;

@Stubbed
@Convert(IdeaPluginConverter.class)
@Presentation(icon = "AllIcons.Nodes.Related")
public interface Dependency extends GenericDomValue<IdeaPlugin> {

  @Stubbed
  @NotNull GenericAttributeValue<Boolean> getOptional();

  /**
   * @see #getResolvedConfigFile()
   */
  @Convert(DependencyConfigFileConverter.class)
  @Stubbed
  @NotNull GenericAttributeValue<PathReference> getConfigFile();

  /**
   * @return {@code null} if {@link #getConfigFile()} not specified or unresolved
   */
  @Nullable XmlFile getResolvedConfigFile();
}
