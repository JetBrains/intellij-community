// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.extended;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor;
import com.intellij.application.options.codeStyle.properties.GeneralCodeStylePropertyMapper;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import com.intellij.util.ObjectUtils;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.EditorConfigFilesCollector;
import org.editorconfig.configmanagement.EditorConfigNavigationActionsFactory;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.core.EditorConfigException;
import org.editorconfig.core.ParsingException;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.editorconfig.core.EditorConfig.OutPair;

@SuppressWarnings("SameParameterValue")
public class EditorConfigCodeStyleSettingsModifier implements CodeStyleSettingsModifier {

  private final static Logger LOG = Logger.getInstance(EditorConfigCodeStyleSettingsModifier.class);
  public static final ProgressIndicator EMPTY_PROGRESS_INDICATOR = new EmptyProgressIndicator();

  private static boolean ourEnabledInTests;

  @Override
  public boolean modifySettings(@NotNull TransientCodeStyleSettings settings, @NotNull PsiFile psiFile) {
    final VirtualFile file = psiFile.getVirtualFile();
    if (Utils.isFullIntellijSettingsSupport() && file != null &&
        (!ApplicationManager.getApplication().isUnitTestMode() || isEnabledInTests())) {
      final Project project = psiFile.getProject();
      if (!project.isDisposed() && Utils.isEnabled(settings)) {
        // Get editorconfig settings
        try {
          final MyContext context = new MyContext(settings, psiFile);
            return runWithCheckCancelled(() -> {
              processEditorConfig(project, psiFile, context);
              // Apply editorconfig settings for the current editor
              if (applyCodeStyleSettings(context)) {
                settings.addDependencies(context.getEditorConfigFiles());
                ObjectUtils.consumeIfNotNull(
                  EditorConfigNavigationActionsFactory.getInstance(psiFile),
                  navigationFactory -> navigationFactory.updateEditorConfigFilePaths(context.getFilePaths())
                );
                return true;
              }
              return false;
            }, getIndicator());
        }
        catch (EditorConfigException e) {
          // TODO: Report an error, ignore for now
        }
        catch (ProcessCanceledException pce) {
          throw pce;
        }
        catch (Exception ex) {
          LOG.error(ex);
        }
      }
    }
    return false;
  }

  private static boolean runWithCheckCancelled(@NotNull Callable<Boolean> callable,
                                               @NotNull ProgressIndicator progressIndicator) throws Exception {
      Future<Boolean> future = ApplicationManager.getApplication().executeOnPooledThread(callable);
      try {
        return ApplicationUtil.runWithCheckCanceled(future, progressIndicator);
      }
      catch (ProcessCanceledException pce) {
        future.cancel(true);
        throw pce;
      }
  }

  @NotNull
  private static ProgressIndicator getIndicator() {
    return ObjectUtils.notNull(ProgressIndicatorProvider.getInstance().getProgressIndicator(), EMPTY_PROGRESS_INDICATOR);
  }

  @Nullable
  @Override
  public CodeStyleStatusBarUIContributor getStatusBarUiContributor(@NotNull TransientCodeStyleSettings transientSettings) {
    return new EditorConfigCodeStyleStatusBarUIContributor();
  }

  private static boolean applyCodeStyleSettings(@NotNull MyContext context) {
    Set<String> processed = new HashSet<>();
    boolean isModified = false;
    for (AbstractCodeStylePropertyMapper mapper : getMappers(context)) {
      processed.clear();
      isModified |= processOptions(context, mapper, false, processed);
      isModified |= processOptions(context, mapper, true, processed);
    }
    return isModified;
  }

  @Override
  public boolean mayOverrideSettingsOf(@NotNull Project project) {
    return Utils.isEnabled(CodeStyle.getSettings(project)) && Utils.editorConfigExists(project);
  }

  @Override
  public String getName() {
    return EditorConfigBundle.message("editorconfig");
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
      CodeStylePropertyAccessor<?> accessor = findAccessor(mapper, intellijName, langPrefix);
      if (accessor != null) {
        final String val = preprocessValue(accessor, context, optionKey, option.getVal());
        for (String dependency : getDependentProperties(optionKey, langPrefix)) {
          if (!processed.contains(dependency)) {
            CodeStylePropertyAccessor<?> dependencyAccessor = findAccessor(mapper, dependency, null);
            if (dependencyAccessor != null) {
              isModified |= dependencyAccessor.setFromString(val);
            }
          }
        }
        isModified |= accessor.setFromString(val);
        processed.add(intellijName);
      }
    }
    return isModified;
  }

  @NotNull
  private static List<String> getDependentProperties(@NotNull String property, @Nullable String langPrefix) {
    property = StringUtil.trimStart(property, EditorConfigIntellijNameUtil.IDE_PREFIX);
    if (langPrefix != null && property.startsWith(langPrefix)) {
      property = StringUtil.trimStart(property, langPrefix);
    }
    if ("indent_size".equals(property)) {
      return Collections.singletonList("continuation_indent_size");
    }
    return Collections.emptyList();
  }

  private static String preprocessValue(@NotNull CodeStylePropertyAccessor accessor,
                                        @NotNull MyContext context,
                                        @NotNull String optionKey,
                                        @NotNull String optionValue) {
    if ("indent_size".equals(optionKey) && "tab".equals(optionValue)) {
      return context.getTabSize();
    }
    else if (EditorConfigValueUtil.EMPTY_LIST_VALUE.equals(optionValue) &&
             CodeStylePropertiesUtil.isAccessorAllowingEmptyList(accessor)) {
      return "";
    }
    return optionValue;
  }

  @Nullable
  private static CodeStylePropertyAccessor<?> findAccessor(@NotNull AbstractCodeStylePropertyMapper mapper,
                                                        @NotNull String propertyName,
                                                        @Nullable String langPrefix) {
    if (langPrefix != null) {
      if (propertyName.startsWith(langPrefix)) {
        final String prefixlessName = StringUtil.trimStart(propertyName, langPrefix);
        final EditorConfigPropertyKind propertyKind = IntellijPropertyKindMap.getPropertyKind(prefixlessName);
        if (propertyKind == EditorConfigPropertyKind.LANGUAGE || propertyKind == EditorConfigPropertyKind.COMMON ||
            EditorConfigIntellijNameUtil.isIndentProperty(prefixlessName)) {
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
    try {
      final VirtualFile file = psiFile.getVirtualFile();
      String filePath = Utils.getFilePath(project, file);
      if (filePath != null) {
        final Set<String> rootDirs = SettingsProviderComponent.getInstance().getRootDirs(project);
        context.setOptions(new EditorConfig().getProperties(filePath, rootDirs, context));
      }
      else {
        if (VfsUtilCore.isBrokenLink(file)) {
          LOG.warn(file.getPresentableUrl() +  " is a broken link");
        }
      }
    }
    catch (ParsingException pe) {
      // Parsing exceptions may occur with incomplete files which is a normal case when .editorconfig is being edited.
      // Thus the error is logged only when debug mode is enabled.
      LOG.debug(pe);
    }
  }

  private static final class MyContext extends EditorConfigFilesCollector {
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

  @Nullable
  @Override
  public Consumer<CodeStyleSettings> getDisablingFunction() {
    return settings -> {
      EditorConfigSettings editorConfigSettings = settings.getCustomSettings(EditorConfigSettings.class);
      editorConfigSettings.ENABLED = false;
    };
  }

  private static boolean isEnabledInTests() {
    return ourEnabledInTests;
  }

  @TestOnly
  public static void setEnabledInTests(boolean isEnabledInTests) {
    ourEnabledInTests = isEnabledInTests;
  }

  private static Collection<AbstractCodeStylePropertyMapper> getMappers(@NotNull MyContext context) {
    Set<AbstractCodeStylePropertyMapper> mappers = new HashSet<>();
    for (LanguageCodeStyleSettingsProvider provider : getLanguageCodeStyleProviders(context)) {
      mappers.add(provider.getPropertyMapper(context.getSettings()));
    }
    mappers.add(new GeneralCodeStylePropertyMapper(context.getSettings()));
    return mappers;
  }

  private static Collection<LanguageCodeStyleSettingsProvider> getLanguageCodeStyleProviders(@NotNull MyContext context) {
    Set<LanguageCodeStyleSettingsProvider> providers = new HashSet<>();
    LanguageCodeStyleSettingsProvider mainProvider = LanguageCodeStyleSettingsProvider.findUsingBaseLanguage(context.getLanguage());
    if (mainProvider != null) {
      providers.add(mainProvider);
    }
    for (String langId : getLanguageIds(context)) {
      if (!langId.equals("any")) {
        LanguageCodeStyleSettingsProvider additionalProvider = LanguageCodeStyleSettingsProvider.findByExternalLanguageId(langId);
        if (additionalProvider != null) {
          providers.add(additionalProvider);
        }
      }
      else {
        providers.clear();
        providers.addAll(LanguageCodeStyleSettingsProvider.EP_NAME.getExtensionList());
        break;
      }
    }
    return providers;
  }

  @NotNull
  private static Collection<String> getLanguageIds(@NotNull MyContext context) {
    Set<String> langIds = new HashSet<>();
    for (OutPair option : context.getOptions()) {
      String key = option.getKey();
      if (EditorConfigIntellijNameUtil.isIndentProperty(key)) {
        langIds.add("any");
      }
      String langId = EditorConfigIntellijNameUtil.extractLanguageDomainId(key);
      if (langId != null) {
        langIds.add(langId);
      }
    }
    return langIds;
  }

}
