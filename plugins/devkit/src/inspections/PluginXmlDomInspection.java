package org.jetbrains.idea.devkit.inspections;

import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

/**
 * @author mike
 */
public class PluginXmlDomInspection extends BasicDomElementsInspection<IdeaPlugin> {
  public PluginXmlDomInspection() {
    super(IdeaPlugin.class);
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return "DevKit";
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Plugin.xml Validity";
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "Plugin.xmlValidity";
  }

  @Nullable
  public String getStaticDescription() {
    return "<html>\n" +
           "<body>\n" +
           "This inspection finds various problems in plugin.xml\n" +
           "</body>\n" +
           "</html>";
  }
}
