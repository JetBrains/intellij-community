// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyleSettingsModifier;
import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleStatusUIContributor;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.TransientCodeStyleSettings;
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
  @NotNull
  @Override
  public Dependencies modifySettings(@NotNull CodeStyleSettings baseSettings, @NotNull PsiFile psiFile) {
    final VirtualFile file = psiFile.getVirtualFile();
    if (Utils.isFullSettingsSupport() && file != null) {
      final Project project = psiFile.getProject();
      if (!project.isDisposed() && Utils.isEnabled(baseSettings)) {
        // Get editorconfig settings
        final List<OutPair> outPairs;
        try {
          outPairs = getEditorConfigOptions(project, psiFile, EditorConfigNavigationActionsFactory.getInstance(file));
          // Apply editorconfig settings for the current editor
          if(applyCodeStyleSettings(outPairs, psiFile, baseSettings)) {
            return getDependencies(EditorConfigNavigationActionsFactory.getInstance(file).getEditorConfigFiles());
          }
        }
        catch (EditorConfigException e) {
          // TODO: Report an error, ignore for now
        }
      }
    }
    return UNMODIFIED;
  }

  @Nullable
  @Override
  public CodeStyleStatusUIContributor getStatusUIContributor(@NotNull TransientCodeStyleSettings transientSettings) {
    return new EditorConfigStatusUIContributor(transientSettings);
  }

  private static boolean applyCodeStyleSettings(@NotNull List<OutPair> editorConfigOptions,
                                                @NotNull PsiFile file,
                                                @NotNull CodeStyleSettings settings) {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(file.getLanguage());
    if (provider != null) {
      AbstractCodeStylePropertyMapper mapper = provider.getPropertyMapper(settings);
      for (OutPair option : editorConfigOptions) {
        mapper.setProperty(option.getKey(), option.getVal());
      }
      return true;
    }
    return false;
  }


  private static DependencyList getDependencies(@NotNull List<VirtualFile> editorConfigFiles) {
    DependencyList dependencies = new DependencyList();
    for (VirtualFile editorConfigFile : editorConfigFiles) {
      dependencies.add(editorConfigFile);
    }
    return dependencies;
  }

  private static List<OutPair> getEditorConfigOptions(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull ParserCallback callback)
    throws EditorConfigException {
    String filePath = Utils.getFilePath(project, psiFile.getVirtualFile());
    final Set<String> rootDirs = SettingsProviderComponent.getInstance().getRootDirs(project);
    return new EditorConfig().getProperties(filePath, rootDirs, callback);
  }

}
