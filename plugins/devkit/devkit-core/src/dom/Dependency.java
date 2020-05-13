// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Stubbed
  GenericAttributeValue<Boolean> getOptional();

  /**
   * @see #getResolvedConfigFile()
   */
  @NotNull
  @Convert(DependencyConfigFileConverter.class)
  @Stubbed
  GenericAttributeValue<PathReference> getConfigFile();

  /**
   * @return {@code null} if {@link #getConfigFile()} not specified or unresolved
   */
  @Nullable
  XmlFile getResolvedConfigFile();
}
