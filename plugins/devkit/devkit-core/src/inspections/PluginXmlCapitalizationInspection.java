// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalQuickFixBase;
import com.intellij.codeInspection.NlsCapitalizationUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

public class PluginXmlCapitalizationInspection extends BasicDomElementsInspection<IdeaPlugin> {

  public PluginXmlCapitalizationInspection() {
    super(IdeaPlugin.class);
  }

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (element instanceof ActionOrGroup) {
      ActionOrGroup actionOrGroup = (ActionOrGroup)element;
      highlightCapitalization(holder, actionOrGroup.getText(), Nls.Capitalization.Title);
      highlightCapitalization(holder, actionOrGroup.getDescription(), Nls.Capitalization.Sentence);
    }
  }

  private static void highlightCapitalization(DomElementAnnotationHolder holder,
                                              GenericDomValue genericDomValue,
                                              Nls.Capitalization capitalization) {
    if (!DomUtil.hasXml(genericDomValue)) return;

    final String stringValue = genericDomValue.getStringValue();
    if (StringUtil.isEmptyOrSpaces(stringValue)) return;

    final String escapedValue = XmlUtil.unescape(stringValue).replace("_", "");
    if (NlsCapitalizationUtil.isCapitalizationSatisfied(escapedValue, capitalization)) {
      return;
    }

    holder.createProblem(genericDomValue,
                         "String '" + escapedValue + "' is not properly capitalized. " +
                         "It should have " + StringUtil.toLowerCase(capitalization.toString()) + " capitalization",
                         new LocalQuickFixBase("Properly capitalize '" + escapedValue + '\'', "Properly capitalize") {

                           @Override
                           public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                             genericDomValue.setStringValue(NlsCapitalizationUtil.fixValue(stringValue, capitalization));
                           }
                         });
  }
}
