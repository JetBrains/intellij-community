// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
import icons.GradleIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.codeInsight.GradlePluginDescriptionsExtension;
import org.jetbrains.plugins.gradle.config.GradleFileType;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class AddGradleDslPluginAction extends CodeInsightAction {
  public static final String ID = "AddGradleDslPluginAction";
  public static final ThreadLocal<String> TEST_THREAD_LOCAL = new ThreadLocal<>();
  private final List<PluginDescriptor> myPlugins;

  public AddGradleDslPluginAction() {
    getTemplatePresentation().setDescription(GradleBundle.messagePointer("gradle.codeInsight.action.apply_plugin.description"));
    getTemplatePresentation().setText(GradleBundle.messagePointer("gradle.codeInsight.action.apply_plugin.text"));
    getTemplatePresentation().setIcon(GradleIcons.Gradle);

    myPlugins = new ArrayList<>();
    for (GradlePluginDescriptionsExtension extension : GradlePluginDescriptionsExtension.EP_NAME.getExtensions()) {
      for (Map.Entry<@NlsSafe String, @NlsContexts.DetailedDescription String> pluginDescription : extension.getPluginDescriptions().entrySet()) {
        myPlugins.add(new PluginDescriptor(pluginDescription.getKey(), pluginDescription.getValue()));
      }
    }
    myPlugins.sort(Comparator.comparing(p -> p.name()));
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new AddGradleDslPluginActionHandler(myPlugins);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    if (psiFile instanceof PsiCompiledElement) return false;
    if (!GradleFileType.isGradleFile(psiFile)) return false;
    return !GradleConstants.SETTINGS_FILE_NAME.equals(psiFile.getName());
  }
}

record PluginDescriptor(@NotNull @NlsSafe String name, @NotNull @NlsContexts.DetailedDescription String description) {
}