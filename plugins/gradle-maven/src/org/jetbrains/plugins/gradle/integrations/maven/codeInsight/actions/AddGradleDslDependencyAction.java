// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.plugins.gradle.config.GradleFileType;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.List;

import static com.intellij.ide.plugins.PluginManagerCore.getPlugin;
import static com.intellij.openapi.extensions.PluginId.getId;
import static java.util.Optional.ofNullable;

/**
 * @author Vladislav.Soroka
 */
public final class AddGradleDslDependencyAction extends CodeInsightAction {
  @ApiStatus.Internal
  public static final ThreadLocal<List<MavenId>> TEST_THREAD_LOCAL = new ThreadLocal<>();

  public AddGradleDslDependencyAction() {
    getTemplatePresentation().setDescription(GradleBundle.messagePointer("gradle.codeInsight.action.add_maven_dependency.description"));
    getTemplatePresentation().setText(GradleBundle.messagePointer("gradle.codeInsight.action.add_maven_dependency.text"));
    getTemplatePresentation().setIcon(AllIcons.Nodes.PpLib);
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new AddGradleDslDependencyActionHandler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    if (packageSearchPluginEnabled()) return false;
    if (psiFile instanceof PsiCompiledElement) return false;
    if (!GradleFileType.isGradleFile(psiFile)) return false;
    return !GradleConstants.SETTINGS_FILE_NAME.equals(psiFile.getName());
  }

  private static @NotNull Boolean packageSearchPluginEnabled() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return false;
    return ofNullable(getPlugin(getId("com.jetbrains.packagesearch.intellij-plugin"))).map(p -> p.isEnabled()).orElse(false);
  }
}
