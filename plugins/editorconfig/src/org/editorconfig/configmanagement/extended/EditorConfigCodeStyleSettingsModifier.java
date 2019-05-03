// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.extended;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.EditorConfigFilesCollector;
import org.editorconfig.configmanagement.EditorConfigNavigationActionsFactory;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.core.EditorConfigException;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.editorconfig.core.EditorConfig.OutPair;

@SuppressWarnings("SameParameterValue")
public class EditorConfigCodeStyleSettingsModifier implements CodeStyleSettingsModifier {
  private final static Map<String,List<String>> DEPENDENCIES = new HashMap<>();

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
        try {
          final MyContext context = new MyContext(settings, psiFile);
          processEditorConfig(project, psiFile, context);
          // Apply editorconfig settings for the current editor
          if (applyCodeStyleSettings(context)) {
            settings.addDependencies(context.getEditorConfigFiles());
            EditorConfigNavigationActionsFactory.getInstance(psiFile.getVirtualFile()).updateEditorConfigFilePaths(context.getFilePaths());
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

  private static boolean applyCodeStyleSettings(@NotNull MyContext context) {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(context.getLanguage());
    if (provider != null) {
      AbstractCodeStylePropertyMapper mapper = provider.getPropertyMapper(context.getSettings());
      Set<String> processed = new HashSet<>();
      boolean isModified = processOptions(context, mapper, false, processed);
      isModified = processOptions(context, mapper, true, processed) || isModified;
      return isModified;

    }
    return false;
  }

  @Override
  public boolean mayOverrideSettingsOf(@NotNull Project project) {
    return Utils.isEnabled(CodeStyle.getSettings(project)) && Utils.editorConfigExists(project);
  }

  @Override
  public String getName() {
    return "EditorConfig";
  }

  private static boolean processOptions(@NotNull MyContext context,
                                        @NotNull AbstractCodeStylePropertyMapper mapper,
                                        boolean languageSpecific,
                                        Set<String> processed) {
    String langPrefix = languageSpecific ? mapper.getLanguageDomainId() + "_" : null;
    boolean isModified = false;
    for (OutPair option : context.getOptions()) {
      final String optionKey = option.getKey();
      String intellijName = EditorConfigIntellijNameUtil.toIntellijName(optionKey);
      CodeStylePropertyAccessor accessor = findAccessor(mapper, intellijName, langPrefix);
      if (accessor != null) {
        final String val = preprocessValue(context, optionKey, option.getVal());
        if (DEPENDENCIES.containsKey(optionKey)) {
          for (String dependency : DEPENDENCIES.get(optionKey)) {
            if (!processed.contains(dependency)) {
              CodeStylePropertyAccessor dependencyAccessor = findAccessor(mapper, dependency, null);
              if (dependencyAccessor != null) {
                isModified |= dependencyAccessor.setFromString(val);
              }
            }
          }
        }
        isModified |= accessor.setFromString(val);
        processed.add(intellijName);
      }
    }
    return isModified;
  }

  private static String preprocessValue(@NotNull MyContext context, @NotNull String optionKey, @NotNull String optionValue) {
    if ("indent_size".equals(optionKey) && "tab".equals(optionValue)) {
      return context.getTabSize();
    }
    return optionValue;
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

  private static void processEditorConfig(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull MyContext context)
    throws EditorConfigException {
    String filePath = Utils.getFilePath(project, psiFile.getVirtualFile());
    final Set<String> rootDirs = SettingsProviderComponent.getInstance().getRootDirs(project);
    context.setOptions(new EditorConfig().getProperties(filePath, rootDirs, context));
  }

  private static class MyContext extends EditorConfigFilesCollector {
    private final @NotNull CodeStyleSettings mySettings;
    private @Nullable List<OutPair> myOptions;
    private final @NotNull PsiFile myFile;

    private MyContext(@NotNull CodeStyleSettings settings, @NotNull PsiFile file) {
      mySettings = settings;
      myFile = file;
    }

    public void setOptions(@NotNull List<OutPair> options) {
      myOptions = options;
    }

    @NotNull
    private CodeStyleSettings getSettings() {
      return mySettings;
    }

    @NotNull
    private List<OutPair> getOptions() {
      return myOptions == null ? Collections.emptyList() : myOptions;
    }

    @NotNull
    private Language getLanguage() {
      return myFile.getLanguage();
    }

    private String getTabSize() {
      for (OutPair pair : getOptions()) {
        if ("tab_width".equals(pair.getKey())) {
          return pair.getVal();
        }
      }
      CommonCodeStyleSettings.IndentOptions indentOptions = mySettings.getIndentOptions(myFile.getFileType());
      return String.valueOf(indentOptions.TAB_SIZE);
    }
  }
}
