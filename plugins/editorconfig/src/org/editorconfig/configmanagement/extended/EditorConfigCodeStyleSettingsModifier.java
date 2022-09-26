// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.extended;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.cache.CodeStyleCachingService;
import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor;
import com.intellij.application.options.codeStyle.properties.GeneralCodeStylePropertyMapper;
import com.intellij.ide.impl.ProjectUtilKt;
import com.intellij.lang.Language;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import com.intellij.util.ObjectUtils;
import org.editorconfig.EditorConfigNotifier;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.EditorConfigFilesCollector;
import org.editorconfig.configmanagement.EditorConfigNavigationActionsFactory;
import org.editorconfig.configmanagement.EditorConfigUsagesCollector;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.core.EditorConfigException;
import org.editorconfig.core.ParsingException;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SuppressWarnings("SameParameterValue")
public final class EditorConfigCodeStyleSettingsModifier implements CodeStyleSettingsModifier {
  private static final Logger LOG = Logger.getInstance(EditorConfigCodeStyleSettingsModifier.class);

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private static boolean ourEnabledInTests;

  private final Set<String> myReportedErrorIds = new HashSet<>();

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
            return runWithTimeout(project, () -> {
              processEditorConfig(project, psiFile, context);
              // Apply editorconfig settings for the current editor
              if (applyCodeStyleSettings(context)) {
                settings.addDependencies(context.getEditorConfigFiles());
                EditorConfigNavigationActionsFactory navigationFactory = EditorConfigNavigationActionsFactory.getInstance(psiFile);
                if (navigationFactory != null) {
                  navigationFactory.updateEditorConfigFilePaths(context.getFilePaths());
                }
                return true;
              }
              return false;
            });
        }
        catch (TimeoutException toe) {
          StackTraceElement[] trace = psiFile.getUserData(CodeStyleCachingService.CALL_TRACE);
          StringBuilder messageBuilder = new StringBuilder();
          if (trace != null) {
            messageBuilder.append("Timeout which searching .editorconfig for ").append(file.getName()).append("\n        at ");
            messageBuilder.append(Arrays.stream(trace)
                                    .skip(1)
                                    .limit(15)
                                    .map(e->e.toString())
                                    .collect(Collectors.joining("\n        at ")));
            LOG.warn(messageBuilder.toString());
          }
          else {
            LOG.warn(toe);
          }
          if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
            error(project, "timeout", EditorConfigBundle.message("error.timeout"), new DisableEditorConfigAction(project), true);
          }
        }
        catch (EditorConfigException e) {
          // TODO: Report an error, ignore for now
        }
        catch (Exception ex) {
          LOG.error(ex);
        }
      }
    }
    return false;
  }

  private static boolean runWithTimeout(@NotNull Project project,
                                        @NotNull Callable<Boolean> callable) throws TimeoutException, EditorConfigException {
    @SuppressWarnings("deprecation")
    CompletableFuture<Boolean> future = ProjectUtilKt.computeOnPooledThread(project, callable);
    try {
      return future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      LOG.warn(e);
    }
    catch (ExecutionException e) {
      if (e.getCause() instanceof EditorConfigException) {
        throw (EditorConfigException)e.getCause();
      }
      LOG.error(e);
    }
    return false;
  }

  public synchronized void error(Project project, String id, @Nls String message, @Nullable AnAction fixAction, boolean oneTime) {
    if (oneTime) {
      if (myReportedErrorIds.contains(id)) return;
      else {
        myReportedErrorIds.add(id);
      }
    }
    Notification notification =
      new Notification(EditorConfigNotifier.GROUP_DISPLAY_ID, EditorConfigBundle.message("editorconfig"), message, NotificationType.ERROR);
    if (fixAction != null) {
      notification.addAction(
        new AnAction(fixAction.getTemplateText()) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            fixAction.actionPerformed(e);
            myReportedErrorIds.remove(id);
            notification.expire();
          }
        }
      );
    }
    Notifications.Bus.notify(notification, project);
  }

  private static class DisableEditorConfigAction extends AnAction {

    private final Project myProject;

    private DisableEditorConfigAction(Project project) {
      super(EditorConfigBundle.message("action.disable"));
      myProject = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      EditorConfigSettings editorConfigSettings =
        CodeStyle.getSettings(myProject).getCustomSettings(EditorConfigSettings.class);
      editorConfigSettings.ENABLED = false;
      CodeStyleSettingsManager.getInstance(myProject).notifyCodeStyleSettingsChanged();
    }
  }

  @Override
  public @NotNull CodeStyleStatusBarUIContributor getStatusBarUiContributor(@NotNull TransientCodeStyleSettings transientSettings) {
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
    if (isModified) {
      ObjectUtils.consumeIfNotNull(
        context.myOptions, options-> EditorConfigUsagesCollector.logEditorConfigUsed(context.myFile, options));
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

  private static @NotNull List<String> getDependentProperties(@NotNull String property, @Nullable String langPrefix) {
    property = Strings.trimStart(property, EditorConfigIntellijNameUtil.IDE_PREFIX);
    if (langPrefix != null && property.startsWith(langPrefix)) {
      property = Strings.trimStart(property, langPrefix);
    }
    if ("indent_size".equals(property)) {
      return Collections.singletonList("continuation_indent_size");
    }
    return Collections.emptyList();
  }

  private static String preprocessValue(@NotNull CodeStylePropertyAccessor<?> accessor,
                                        @NotNull MyContext context,
                                        @NotNull String optionKey,
                                        @NotNull String optionValue) {
    if ("indent_size".equals(optionKey)) {
      String explicitTabSize = context.getExplicitTabSize();
      if ("tab".equals(optionValue)) {
        return ObjectUtils.notNull(explicitTabSize, context.getDefaultTabSize());
      }
      else if (context.isTabIndent() && explicitTabSize != null) {
        return explicitTabSize;
      }
    }
    else if (EditorConfigValueUtil.EMPTY_LIST_VALUE.equals(optionValue) &&
             CodeStylePropertiesUtil.isAccessorAllowingEmptyList(accessor)) {
      return "";
    }
    return optionValue;
  }

  private static @Nullable CodeStylePropertyAccessor<?> findAccessor(@NotNull AbstractCodeStylePropertyMapper mapper,
                                                                     @NotNull String propertyName,
                                                                     @Nullable String langPrefix) {
    if (langPrefix != null) {
      if (propertyName.startsWith(langPrefix)) {
        final String prefixlessName = Strings.trimStart(propertyName, langPrefix);
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
      // Thus, the error is logged only when debug mode is enabled.
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

    private @NotNull CodeStyleSettings getSettings() {
      return mySettings;
    }

    private @NotNull List<OutPair> getOptions() {
      return myOptions == null ? Collections.emptyList() : myOptions;
    }

    private @NotNull Language getLanguage() {
      return myFile.getLanguage();
    }

    private @NotNull String getDefaultTabSize() {
      CommonCodeStyleSettings.IndentOptions indentOptions = mySettings.getIndentOptions(myFile.getFileType());
      return String.valueOf(indentOptions.TAB_SIZE);
    }

    private @Nullable String getExplicitTabSize() {
      for (OutPair pair : getOptions()) {
        if ("tab_width".equals(pair.getKey())) {
          return pair.getVal();
        }
      }
      return null;
    }

    private boolean isTabIndent() {
      for (OutPair pair : getOptions()) {
        if ("indent_style".equals(pair.getKey())) {
          return "tab".equals(pair.getVal());
        }
      }
      return false;
    }
  }

  @Override
  public @NotNull Consumer<CodeStyleSettings> getDisablingFunction(@NotNull Project project) {
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
        providers.addAll(LanguageCodeStyleSettingsProvider.getAllProviders());
        break;
      }
    }
    return providers;
  }

  private static @NotNull Collection<String> getLanguageIds(@NotNull MyContext context) {
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
