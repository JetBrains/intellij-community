// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.core.EditorConfigException;
import org.editorconfig.core.ParserCallback;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static org.editorconfig.core.EditorConfig.OutPair;

public class EditorConfigCodeStyleSettingsModifier implements CodeStyleSettingsModifier {
  @Override
  public boolean modifySettings(@NotNull TransientCodeStyleSettings settings, @NotNull PsiFile psiFile) {
    final VirtualFile file = psiFile.getVirtualFile();
    if (Utils.isFullSettingsSupport() && file != null) {
      final Project project = psiFile.getProject();
      if (!project.isDisposed() && Utils.isEnabled(settings)) {
        // Get editorconfig settings
        final List<OutPair> outPairs;
        try {
          outPairs = getEditorConfigOptions(project, psiFile, EditorConfigNavigationActionsFactory.getInstance(file));
          // Apply editorconfig settings for the current editor
          if(applyCodeStyleSettings(outPairs, psiFile, settings)) {
            settings.addDependencies(EditorConfigNavigationActionsFactory.getInstance(file).getEditorConfigFiles());
            return true;
          }
        }
        catch (EditorConfigException e) {
          // TODO: Report an error, ignore for now
        }
      }
    }
    return false;
  }

  @Nullable
  @Override
  public CodeStyleStatusBarUIContributor getStatusBarUiContributor(@NotNull TransientCodeStyleSettings transientSettings) {
    return new EditorConfigStatusUIContributor(transientSettings);
  }

  private static boolean applyCodeStyleSettings(@NotNull List<OutPair> editorConfigOptions,
                                                @NotNull PsiFile file,
                                                @NotNull CodeStyleSettings settings) {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(file.getLanguage());
    if (provider != null) {
      boolean isModified = false;
      AbstractCodeStylePropertyMapper mapper = provider.getPropertyMapper(settings);
      for (OutPair option : editorConfigOptions) {
        if (mapper.setProperty(option.getKey(), option.getVal())) {
          isModified = true;
        }
      }
      return isModified;
    }
    return false;
  }

  private static List<OutPair> getEditorConfigOptions(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull ParserCallback callback)
    throws EditorConfigException {
    String filePath = Utils.getFilePath(project, psiFile.getVirtualFile());
    final Set<String> rootDirs = SettingsProviderComponent.getInstance().getRootDirs(project);
    return new EditorConfig().getProperties(filePath, rootDirs, callback);
  }

}
