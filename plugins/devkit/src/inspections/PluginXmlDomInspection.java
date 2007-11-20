package org.jetbrains.idea.devkit.inspections;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
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

  protected void checkDomElement(final DomElement element, final DomElementAnnotationHolder holder, final DomHighlightingHelper helper) {
    super.checkDomElement(element, holder, helper);

    if (element instanceof Extension) {
      Extension extension = (Extension)element;
      checkExtension(extension, holder, helper);
    }
  }

  private void checkExtension(final Extension extension, final DomElementAnnotationHolder holder, final DomHighlightingHelper helper) {
  }
}
