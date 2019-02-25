// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.extended;

import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.EditorConfigNavigationActionsFactory;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.core.EditorConfigException;
import org.editorconfig.core.ParserCallback;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.editorconfig.core.EditorConfig.OutPair;

@SuppressWarnings("SameParameterValue")
public class EditorConfigCodeStyleSettingsModifier implements CodeStyleSettingsModifier {
  private final static Map<String,List<String>> DEPENDENCIES = ContainerUtil.newHashMap();
  static {
    addDependency("indent_size", "continuation_indent_size");
  }

  private static void addDependency(@NotNull String name, String... dependentNames) {
    DEPENDENCIES.put(name, Arrays.asList(dependentNames));
  }

  @Override
  public boolean modifySettings(@NotNull TransientCodeStyleSettings settings, @NotNull PsiFile psiFile) {
    final VirtualFile file = psiFile.getVirtualFile();
    if (Utils.isFullIntellijSettingsSupport() && file != null) {
      final Project project = psiFile.getProject();
      if (!project.isDisposed() && Utils.isEnabled(settings)) {
        // Get editorconfig settings
        final List<OutPair> outPairs;
        try {
          outPairs = getEditorConfigOptions(project, psiFile, EditorConfigNavigationActionsFactory.getInstance(file));
          // Apply editorconfig settings for the current editor
          if (applyCodeStyleSettings(outPairs, psiFile, settings)) {
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
    return new EditorConfigCodeStyleStatusBarUIContributor();
  }

  private static boolean applyCodeStyleSettings(@NotNull List<OutPair> editorConfigOptions,
                                                @NotNull PsiFile file,
                                                @NotNull CodeStyleSettings settings) {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(file.getLanguage());
    if (provider != null) {
      AbstractCodeStylePropertyMapper mapper = provider.getPropertyMapper(settings);
      Set<String> processed = ContainerUtil.newHashSet();
      boolean isModified = processOptions(editorConfigOptions, mapper, false, processed);
      isModified = processOptions(editorConfigOptions, mapper, true, processed) || isModified;
      return isModified;

    }
    return false;
  }

  private static boolean processOptions(@NotNull List<OutPair> editorConfigOptions,
                                        @NotNull AbstractCodeStylePropertyMapper mapper,
                                        boolean languageSpecific,
                                        Set<String> processed) {
    String langPrefix = languageSpecific ? mapper.getLanguageDomainId() + "_" : null;
    boolean isModified = false;
    for (OutPair option : editorConfigOptions) {
      final String optionKey = option.getKey();
      String intellijName = EditorConfigIntellijNameUtil.toIntellijName(optionKey);
      CodeStylePropertyAccessor accessor = findAccessor(mapper, intellijName, langPrefix);
      if (accessor != null) {
        if (DEPENDENCIES.containsKey(optionKey)) {
          for (String dependency : DEPENDENCIES.get(optionKey)) {
            if (!processed.contains(dependency)) {
              CodeStylePropertyAccessor dependencyAccessor = findAccessor(mapper, dependency, null);
              if (dependencyAccessor != null) {
                isModified |= dependencyAccessor.setFromString(option.getVal());
              }
            }
          }
        }
        isModified |= accessor.setFromString(option.getVal());
        processed.add(intellijName);
      }
    }
    return isModified;
  }

  @Nullable
  private static CodeStylePropertyAccessor findAccessor(@NotNull AbstractCodeStylePropertyMapper mapper,
                                                        @NotNull String propertyName,
                                                        @Nullable String langPrefix) {
    if (langPrefix != null) {
      if (propertyName.startsWith(langPrefix)) {
        final String prefixlessName = StringUtil.trimStart(propertyName, langPrefix);
        final EditorConfigPropertyKind propertyKind = IntellijPropertyKindMap.getPropertyKind(prefixlessName);
        if (propertyKind == EditorConfigPropertyKind.LANGUAGE || propertyKind == EditorConfigPropertyKind.COMMON) {
          return mapper.getAccessor(prefixlessName);
        }
      }
    }
    else {
      return mapper.getAccessor(propertyName);
    }
    return null;
  }

  private static List<OutPair> getEditorConfigOptions(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull ParserCallback callback)
    throws EditorConfigException {
    String filePath = Utils.getFilePath(project, psiFile.getVirtualFile());
    final Set<String> rootDirs = SettingsProviderComponent.getInstance().getRootDirs(project);
    return new EditorConfig().getProperties(filePath, rootDirs, callback);
  }
}
