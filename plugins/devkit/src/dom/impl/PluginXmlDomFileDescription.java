package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

/**
 * @author mike
 */
public class PluginXmlDomFileDescription extends DomFileDescription<IdeaPlugin> {
  public PluginXmlDomFileDescription() {
    super(IdeaPlugin.class, "idea-plugin");
  }

  protected void initializeFileDescription() {
    super.initializeFileDescription();
    registerImplementation(IdeaPlugin.class, IdeaPluginImpl.class);
  }

  public boolean isMyFile(@NotNull final XmlFile file, @Nullable final Module module) {
    return super.isMyFile(file, module);
  }
}
