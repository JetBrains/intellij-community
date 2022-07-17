// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
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
public class AddGradleDslDependencyAction extends CodeInsightAction {
  static final ThreadLocal<List<MavenId>> TEST_THREAD_LOCAL = new ThreadLocal<>();

  public AddGradleDslDependencyAction() {
    getTemplatePresentation().setDescription(GradleBundle.messagePointer("gradle.codeInsight.action.add_maven_dependency.description"));
    getTemplatePresentation().setText(GradleBundle.messagePointer("gradle.codeInsight.action.add_maven_dependency.text"));
    getTemplatePresentation().setIcon(AllIcons.Nodes.PpLib);
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new AddGradleDslDependencyActionHandler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (packageSearchPluginEnabled()) return false;
    if (file instanceof PsiCompiledElement) return false;
    if (!GradleFileType.isGradleFile(file)) return false;
    return !GradleConstants.SETTINGS_FILE_NAME.equals(file.getName());
  }

  @NotNull
  private static Boolean packageSearchPluginEnabled() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return false;
    return ofNullable(getPlugin(getId("com.jetbrains.packagesearch.intellij-plugin"))).map(p -> p.isEnabled()).orElse(false);
  }
}
