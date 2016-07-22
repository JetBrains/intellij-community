/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.KeyValue;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.codeInsight.GradlePluginDescriptionsExtension;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 10/22/13
 */
public class AddGradleDslPluginAction extends CodeInsightAction {
  public static final String ID = "AddGradleDslPluginAction";
  static final ThreadLocal<String> TEST_THREAD_LOCAL = new ThreadLocal<>();
  private final KeyValue[] myPlugins;

  public AddGradleDslPluginAction() {
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.codeInsight.action.apply_plugin.description"));
    getTemplatePresentation().setText(GradleBundle.message("gradle.codeInsight.action.apply_plugin.text"));
    getTemplatePresentation().setIcon(GradleIcons.GradlePlugin);

    Collection<KeyValue> pluginDescriptions = new ArrayList<>();
    for (GradlePluginDescriptionsExtension extension : GradlePluginDescriptionsExtension.EP_NAME.getExtensions()) {
      for (Map.Entry<String, String> pluginDescription : extension.getPluginDescriptions().entrySet()) {
        pluginDescriptions.add(KeyValue.create(pluginDescription.getKey(), pluginDescription.getValue()));
      }
    }

    myPlugins = pluginDescriptions.toArray(new KeyValue[pluginDescriptions.size()]);
    Arrays.sort(myPlugins, (o1, o2) -> String.valueOf(o1.getKey()).compareTo(String.valueOf(o2.getKey())));
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new AddGradleDslPluginActionHandler(myPlugins);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (file instanceof PsiCompiledElement) return false;
    if (!GroovyFileType.GROOVY_FILE_TYPE.equals(file.getFileType())) return false;
    return !GradleConstants.SETTINGS_FILE_NAME.equals(file.getName()) && file.getName().endsWith(GradleConstants.EXTENSION);
  }
}
