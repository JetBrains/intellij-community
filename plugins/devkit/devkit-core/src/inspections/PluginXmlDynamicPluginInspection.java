// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.highlighting.AddDomElementQuickFix;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.*;

import javax.swing.*;
import java.util.Objects;

public class PluginXmlDynamicPluginInspection extends DevKitPluginXmlInspectionBase {
  public boolean highlightNonDynamicEPUsages = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Highlight usage of non-dynamic extension points", this, "highlightNonDynamicEPUsages");
  }

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (element instanceof ApplicationComponents ||
        element instanceof ProjectComponents ||
        element instanceof ModuleComponents) {
      highlightComponents(holder, element);
    }

    else if (element instanceof ExtensionPoint) {
      highlightExtensionPoint(holder, ((ExtensionPoint)element));
    }

    else if (element instanceof Group) {
      highlightGroup(holder, (Group)element);
    }

    else if (highlightNonDynamicEPUsages && element instanceof Extension) {
      highlightExtension(holder, ((Extension)element));
    }
  }

  private static void highlightComponents(DomElementAnnotationHolder holder, DomElement component) {
    holder.createProblem(component,
                         "<html>Non-dynamic plugin due to using components, replace with " +
                         "<a href=\"https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_components.html\">alternatives</a></html>");
  }

  private static void highlightExtensionPoint(DomElementAnnotationHolder holder, ExtensionPoint extensionPoint) {
    if (!DomUtil.hasXml(extensionPoint.getDynamic())) {
      final LocalQuickFix[] fixes = holder.isOnTheFly() && ApplicationManager.getApplication().isInternal() ? new LocalQuickFix[]{
        createAnalyzeEPFix("AnalyzeEPUsage", extensionPoint),
        createAnalyzeEPFix("AnalyzeEPUsageIgnoreSafeClasses", extensionPoint)
      } : LocalQuickFix.EMPTY_ARRAY;
      holder.createProblem(extensionPoint, "Non-dynamic extension point '" + extensionPoint.getEffectiveQualifiedName() + "'",
                           fixes);
    }
    else if (Boolean.FALSE == extensionPoint.getDynamic().getValue()) {
      holder.createProblem(extensionPoint, "Explicit non-dynamic extension point '" + extensionPoint.getEffectiveQualifiedName() + "'");
    }
  }

  private static IntentionAndQuickFixAction createAnalyzeEPFix(String actionId, ExtensionPoint extensionPoint) {
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    assert action != null : actionId;

    String name = action.getTemplateText() + " for '" + extensionPoint.getEffectiveQualifiedName() + "'";
    return new IntentionAndQuickFixAction() {
      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getName() {
        return name;
      }

      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getFamilyName() {
        return Objects.requireNonNull(action.getTemplateText());
      }

      @Override
      public void applyFix(@NotNull Project project, PsiFile file, @Nullable Editor editor) {
        assert editor != null;
        action.actionPerformed(AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, ((EditorEx)editor).getDataContext()));
      }
    };
  }

  private static void highlightGroup(DomElementAnnotationHolder holder, Group group) {
    if (!DomUtil.hasXml(group.getId())) {
      holder.createProblem(group, "'id' must be specified for <group>", new AddDomElementQuickFix<>(group.getId()));
    }
  }

  private static void highlightExtension(DomElementAnnotationHolder holder, Extension extension) {
    final ExtensionPoint extensionPoint = extension.getExtensionPoint();

    if (extensionPoint != null && Boolean.TRUE != extensionPoint.getDynamic().getValue()) {
      holder.createProblem(extension, "Usage of non-dynamic extension point '" + extensionPoint.getEffectiveQualifiedName() + "'");
    }
  }
}
