// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.compilerPreferences.configuration;

import com.intellij.compiler.server.BuildManager;
import com.intellij.icons.AllIcons;
import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.MutableCollectionComboBoxModel;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import kotlin.KotlinVersion;
import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.kotlin.cli.common.arguments.*;
import org.jetbrains.kotlin.config.*;
import org.jetbrains.kotlin.idea.PluginStartupApplicationService;
import org.jetbrains.kotlin.idea.base.compilerPreferences.KotlinBaseCompilerConfigurationUiBundle;
import org.jetbrains.kotlin.idea.base.compilerPreferences.facet.DescriptionListCellRenderer;
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants;
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils;
import org.jetbrains.kotlin.idea.base.util.ProjectStructureUtils;
import org.jetbrains.kotlin.idea.compiler.configuration.*;
import org.jetbrains.kotlin.idea.facet.KotlinFacet;
import org.jetbrains.kotlin.idea.util.application.ApplicationUtilsKt;
import org.jetbrains.kotlin.platform.IdePlatformKind;
import org.jetbrains.kotlin.platform.PlatformUtilKt;
import org.jetbrains.kotlin.platform.TargetPlatform;
import org.jetbrains.kotlin.platform.impl.JsIdePlatformUtil;
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind;
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformUtil;
import org.jetbrains.kotlin.platform.jvm.JdkPlatform;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.openapi.options.Configurable.isCheckboxModified;
import static com.intellij.openapi.options.Configurable.isFieldModified;

public class KotlinCompilerConfigurableTab implements SearchableConfigurable, Disposable {
    private static final Map<String, @NlsSafe String> moduleKindDescriptions = new LinkedHashMap<>();
    private static final Map<String, @NlsSafe String> sourceMapSourceEmbeddingDescriptions = new LinkedHashMap<>();
    private static final int MAX_WARNING_SIZE = 75;

    static {
        moduleKindDescriptions.put(K2JsArgumentConstants.MODULE_PLAIN,
                                   KotlinBaseCompilerConfigurationUiBundle.message("configuration.description.plain.put.to.global.scope"));
        moduleKindDescriptions.put(K2JsArgumentConstants.MODULE_AMD, KotlinBaseCompilerConfigurationUiBundle.message("configuration.description.amd"));
        moduleKindDescriptions.put(K2JsArgumentConstants.MODULE_COMMONJS, KotlinBaseCompilerConfigurationUiBundle.message("configuration.description.commonjs"));
        moduleKindDescriptions.put(K2JsArgumentConstants.MODULE_UMD, KotlinBaseCompilerConfigurationUiBundle.message(
                "configuration.description.umd.detect.amd.or.commonjs.if.available.fallback.to.plain"));

        sourceMapSourceEmbeddingDescriptions
                .put(K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_NEVER, KotlinBaseCompilerConfigurationUiBundle.message("configuration.description.never"));
        sourceMapSourceEmbeddingDescriptions
                .put(K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_ALWAYS, KotlinBaseCompilerConfigurationUiBundle.message("configuration.description.always"));
        sourceMapSourceEmbeddingDescriptions.put(K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_INLINING, KotlinBaseCompilerConfigurationUiBundle.message(
                "configuration.description.when.inlining.a.function.from.other.module.with.embedded.sources"));
    }

    @Nullable
    private final KotlinCompilerWorkspaceSettings compilerWorkspaceSettings;
    private final Project project;
    private final boolean isProjectSettings;
    private CommonCompilerArguments commonCompilerArguments;
    private K2JSCompilerArguments k2jsCompilerArguments;
    private K2JVMCompilerArguments k2jvmCompilerArguments;
    private CompilerSettings compilerSettings;
    private final @Nullable JpsPluginSettings jpsPluginSettings;
    private JPanel contentPane;
    private ThreeStateCheckBox reportWarningsCheckBox;
    private RawCommandLineEditor additionalArgsOptionsField;
    private JLabel additionalArgsLabel;
    private ThreeStateCheckBox generateSourceMapsCheckBox;
    private TextFieldWithBrowseButton outputPrefixFile;
    private TextFieldWithBrowseButton outputPostfixFile;
    private JLabel labelForOutputDirectory;
    private TextFieldWithBrowseButton outputDirectory;
    private ThreeStateCheckBox copyRuntimeFilesCheckBox;
    private ThreeStateCheckBox keepAliveCheckBox;
    private JCheckBox enableIncrementalCompilationForJvmCheckBox;
    private JCheckBox enableIncrementalCompilationForJsCheckBox;
    private JComboBox<String> moduleKindComboBox;
    private JTextField scriptTemplatesField;
    private JTextField scriptTemplatesClasspathField;
    private JPanel k2jvmPanel;
    private JPanel k2jsPanel;
    private JComboBox<String> jvmVersionComboBox;
    private JPanel kotlinJpsPluginVersionPanel;
    private JComboBox<JpsVersionItem> kotlinJpsPluginVersionComboBox;
    private ComboBoxModelWithPossiblyDisabledItems jpsPluginComboBoxModel;
    private JpsVersionItem defaultJpsVersionItem;
    private JComboBox<VersionView> languageVersionComboBox;
    private JComboBox<VersionView> apiVersionComboBox;
    private JPanel scriptPanel;
    private JLabel labelForOutputPrefixFile;
    private JLabel labelForOutputPostfixFile;
    private JLabel warningLabel;
    private JTextField sourceMapPrefix;
    private JComboBox<String> sourceMapEmbedSources;
    private boolean isEnabled = true;

    public KotlinCompilerConfigurableTab(
            Project project,
            @NotNull CommonCompilerArguments commonCompilerArguments,
            @NotNull K2JSCompilerArguments k2jsCompilerArguments,
            @NotNull K2JVMCompilerArguments k2jvmCompilerArguments,
            @NotNull CompilerSettings compilerSettings,
            @Nullable KotlinCompilerWorkspaceSettings compilerWorkspaceSettings,
            boolean isProjectSettings,
            boolean isMultiEditor
    ) {
        this.project = project;
        this.commonCompilerArguments = commonCompilerArguments;
        this.k2jsCompilerArguments = k2jsCompilerArguments;
        this.compilerSettings = compilerSettings;
        this.jpsPluginSettings = Optional.ofNullable(isProjectSettings ? KotlinJpsPluginSettings.getInstance(project) : null)
                .map(KotlinJpsPluginSettings::getSettings)
                .map(FreezableKt::unfrozen)
                .orElse(null);
        this.compilerWorkspaceSettings = compilerWorkspaceSettings;
        this.k2jvmCompilerArguments = k2jvmCompilerArguments;
        this.isProjectSettings = isProjectSettings;

        warningLabel.setIcon(AllIcons.General.WarningDialog);

        if (isProjectSettings) {
            languageVersionComboBox.addActionListener(e -> onLanguageLevelChanged(getSelectedLanguageVersionView()));
        }

        additionalArgsOptionsField.attachLabel(additionalArgsLabel);

        fillVersions();

        if (KotlinPlatformUtils.isCidr()) {
            keepAliveCheckBox.setVisible(false);
            k2jvmPanel.setVisible(false);
            k2jsPanel.setVisible(false);
        } else {
            initializeNonCidrSettings(isMultiEditor);
        }

        reportWarningsCheckBox.setThirdStateEnabled(isMultiEditor);

        if (isProjectSettings) {
            List<String> modulesOverridingProjectSettings = ArraysKt.mapNotNull(
                    ModuleManager.getInstance(project).getModules(),
                    module -> {
                        KotlinFacet facet = KotlinFacet.Companion.get(module);
                        if (facet == null) return null;
                        KotlinFacetSettings facetSettings = facet.getConfiguration().getSettings();
                        if (facetSettings.getUseProjectSettings()) return null;
                        return module.getName();
                    }
            );
            CollectionsKt.sort(modulesOverridingProjectSettings);
            if (!modulesOverridingProjectSettings.isEmpty()) {
                warningLabel.setVisible(true);
                warningLabel.setText(buildOverridingModulesWarning(modulesOverridingProjectSettings));
            }
        }
    }

    @SuppressWarnings("unused") // Empty constructor fixes 'Extension should not have constructor with parameters (except Project)'
    public KotlinCompilerConfigurableTab(Project project) {
        this(project,
             FreezableKt.unfrozen(KotlinCommonCompilerArgumentsHolder.getInstance(project).getSettings()),
             FreezableKt.unfrozen(Kotlin2JsCompilerArgumentsHolder.getInstance(project).getSettings()),
             FreezableKt.unfrozen(Kotlin2JvmCompilerArgumentsHolder.getInstance(project).getSettings()),
             FreezableKt.unfrozen(KotlinCompilerSettings.getInstance(project).getSettings()),
             KotlinCompilerWorkspaceSettings.getInstance(project),
             true,
             false);
    }

    private void initializeNonCidrSettings(boolean isMultiEditor) {
        setupFileChooser(labelForOutputPrefixFile, outputPrefixFile,
                         KotlinBaseCompilerConfigurationUiBundle.message("configuration.title.kotlin.compiler.js.option.output.prefix.browse.title"),
                         true);
        setupFileChooser(labelForOutputPostfixFile, outputPostfixFile,
                         KotlinBaseCompilerConfigurationUiBundle.message("configuration.title.kotlin.compiler.js.option.output.postfix.browse.title"),
                         true);
        setupFileChooser(labelForOutputDirectory, outputDirectory,
                         KotlinBaseCompilerConfigurationUiBundle.message("configuration.title.choose.output.directory"),
                         false);

        fillModuleKindList();
        fillSourceMapSourceEmbeddingList();
        fillJvmVersionList();

        generateSourceMapsCheckBox.setThirdStateEnabled(isMultiEditor);
        generateSourceMapsCheckBox.addActionListener(event -> sourceMapPrefix.setEnabled(generateSourceMapsCheckBox.isSelected()));

        copyRuntimeFilesCheckBox.setThirdStateEnabled(isMultiEditor);
        keepAliveCheckBox.setThirdStateEnabled(isMultiEditor);

        if (compilerWorkspaceSettings == null) {
            keepAliveCheckBox.setVisible(false);
            k2jvmPanel.setVisible(false);
            enableIncrementalCompilationForJsCheckBox.setVisible(false);
        }

        updateOutputDirEnabled();
    }

    @Override
    public void dispose() {

    }

    private static int calculateNameCountToShowInWarning(List<String> allNames) {
        int lengthSoFar = 0;
        int size = allNames.size();
        for (int i = 0; i < size; i++) {
            lengthSoFar = (i > 0 ? lengthSoFar + 2 : 0) + allNames.get(i).length();
            if (lengthSoFar > MAX_WARNING_SIZE) return i;
        }
        return size;
    }

    @NotNull
    private static @NlsSafe String buildOverridingModulesWarning(List<String> modulesOverridingProjectSettings) {
        int nameCountToShow = calculateNameCountToShowInWarning(modulesOverridingProjectSettings);
        int allNamesCount = modulesOverridingProjectSettings.size();
        if (nameCountToShow == 0) {
            return KotlinBaseCompilerConfigurationUiBundle.message("configuration.warning.text.modules.override.project.settings", String.valueOf(allNamesCount));
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<html>");
        builder.append(KotlinBaseCompilerConfigurationUiBundle.message("configuration.warning.text.following.modules.override.project.settings")).append(" ");
        CollectionsKt.joinTo(
                modulesOverridingProjectSettings.subList(0, nameCountToShow),
                builder,
                ", ",
                "",
                "",
                -1,
                "",
                new Function1<>() {
                    @Override
                    public CharSequence invoke(String s) {
                        return "<strong>" + s + "</strong>";
                    }
                }
        );
        if (nameCountToShow < allNamesCount) {
            builder.append(" ").append(KotlinBaseCompilerConfigurationUiBundle.message("configuration.text.and")).append(" ").append(allNamesCount - nameCountToShow)
                    .append(" ").append(KotlinBaseCompilerConfigurationUiBundle.message("configuration.text.other.s"));
        }
        return builder.toString();
    }

    @NotNull
    private static @Nls
    String getModuleKindDescription(@Nullable String moduleKind) {
        if (moduleKind == null) return "";
        String result = moduleKindDescriptions.get(moduleKind);
        assert result != null : "Module kind " + moduleKind + " was not added to combobox, therefore it should not be here";
        return result;
    }

    @NotNull
    private static @Nls
    String getSourceMapSourceEmbeddingDescription(@Nullable String sourceMapSourceEmbeddingId) {
        if (sourceMapSourceEmbeddingId == null) return "";
        String result = sourceMapSourceEmbeddingDescriptions.get(sourceMapSourceEmbeddingId);
        assert result != null : "Source map source embedding mode " + sourceMapSourceEmbeddingId +
                                " was not added to combobox, therefore it should not be here";
        return result;
    }

    @NotNull
    private static @NlsSafe String getModuleKindOrDefault(@Nullable String moduleKindId) {
        if (moduleKindId == null) {
            moduleKindId = K2JsArgumentConstants.MODULE_PLAIN;
        }
        return moduleKindId;
    }

    @NotNull
    private static @NlsSafe String getSourceMapSourceEmbeddingOrDefault(@Nullable String sourceMapSourceEmbeddingId) {
        if (sourceMapSourceEmbeddingId == null) {
            sourceMapSourceEmbeddingId = K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_INLINING;
        }
        return sourceMapSourceEmbeddingId;
    }

    private static @NlsSafe String getJvmVersionOrDefault(@Nullable String jvmVersion) {
        return jvmVersion != null ? jvmVersion : JvmTarget.DEFAULT.getDescription();
    }

    private static void setupFileChooser(
            @NotNull JLabel label,
            @NotNull TextFieldWithBrowseButton fileChooser,
            @NotNull @NlsContexts.DialogTitle String title,
            boolean forFiles
    ) {
        label.setLabelFor(fileChooser);

        fileChooser.addBrowseFolderListener(title, null, null,
                                            new FileChooserDescriptor(forFiles, !forFiles, false, false, false, false),
                                            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    }

    private static boolean isBrowseFieldModifiedWithNullize(@NotNull TextFieldWithBrowseButton chooser, @Nullable String currentValue) {
        return !StringUtil.equals(
                StringUtil.nullize(chooser.getText(), true),
                StringUtil.nullize(currentValue, true));
    }

    private static boolean isBrowseFieldModified(@NotNull TextFieldWithBrowseButton chooser, @NotNull String currentValue) {
        return !StringUtil.equals(chooser.getText(), currentValue);
    }

    private void updateOutputDirEnabled() {
        if (isEnabled && copyRuntimeFilesCheckBox != null) {
            outputDirectory.setEnabled(copyRuntimeFilesCheckBox.isSelected());
            labelForOutputDirectory.setEnabled(copyRuntimeFilesCheckBox.isSelected());
        }
    }

    private static boolean isLessOrEqual(LanguageOrApiVersion version, LanguageOrApiVersion upperBound) {
        return VersionComparatorUtil.compare(version.getVersionString(), upperBound.getVersionString()) <= 0;
    }

    public void onLanguageLevelChanged(@Nullable VersionView languageLevel) {
        if (languageLevel == null) return;
        restrictAPIVersions(languageLevel);
    }

    private void restrictAPIVersions(VersionView upperBoundView) {
        VersionView selectedAPIView = getSelectedAPIVersionView();
        LanguageOrApiVersion selectedAPIVersion = selectedAPIView.getVersion();
        LanguageOrApiVersion upperBound = upperBoundView.getVersion();
        List<VersionView> permittedAPIVersions = new ArrayList<>(LanguageVersion.values().length + 1);
        if (isLessOrEqual(VersionView.LatestStable.INSTANCE.getVersion(), upperBound)) {
            permittedAPIVersions.add(VersionView.LatestStable.INSTANCE);
        }
        ArraysKt.mapNotNullTo(
                LanguageVersion.values(),
                permittedAPIVersions,
                languageVersion -> {
                    ApiVersion apiVersion = ApiVersion.createByLanguageVersion(languageVersion);
                    if (isLessOrEqual(apiVersion, upperBound) && !apiVersion.isUnsupported()) {
                        return new VersionView.Specific(languageVersion);
                    }

                    return null;
                }
        );

        apiVersionComboBox.setModel(new MutableCollectionComboBoxModel<>(permittedAPIVersions));

        apiVersionComboBox.setSelectedItem(
                VersionComparatorUtil.compare(selectedAPIVersion.getVersionString(), upperBound.getVersionString()) <= 0
                ? selectedAPIView
                : upperBoundView
        );
    }

    private void fillJvmVersionList() {
        for (TargetPlatform jvm : JvmIdePlatformKind.INSTANCE.getPlatforms()) {
            JvmTarget jvmTarget = PlatformUtilKt.subplatformsOfType(jvm, JdkPlatform.class).get(0).getTargetVersion();
            @NlsSafe String description = jvmTarget.getDescription();
            if (jvmTarget == JvmTarget.JVM_1_6) {
                description += " " + KotlinBaseCompilerConfigurationUiBundle.message("deprecated.jvm.version");
            }

            jvmVersionComboBox.addItem(description);
        }
    }

    private void fetchAvailableJpsCompilersAsync(Consumer<? super @NlsSafe @Nullable Collection<IdeKotlinVersion>> onFinish) {
        JarRepositoryManager.getAvailableVersions(project, RepositoryLibraryDescription.findDescription(
                        KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID, KotlinArtifactConstants.KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID))
                .onProcessed(distVersions -> {
                    if (distVersions == null) {
                        onFinish.accept(null);
                        return;
                    }
                    JarRepositoryManager.getAvailableVersions(project, RepositoryLibraryDescription.findDescription(
                                    KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID,
                                    KotlinArtifactConstants.KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID))
                            .onProcessed(jpsClassPathVersions -> {
                                if (jpsClassPathVersions == null) {
                                    onFinish.accept(null);
                                    return;
                                }

                                KotlinVersion min = KotlinJpsPluginSettings.getJpsMinimumSupportedVersion();
                                KotlinVersion max = KotlinJpsPluginSettings.getJpsMaximumSupportedVersion();
                                HashSet<IdeKotlinVersion> ideKotlinVersions = new HashSet<>();
                                for (String version : distVersions) {
                                    if (!jpsClassPathVersions.contains(version)) continue;

                                    IdeKotlinVersion parsedVersion = IdeKotlinVersion.opt(version);
                                    if (parsedVersion != null) {
                                        KotlinVersion parsedKotlinVersion = parsedVersion.getKotlinVersion();
                                        if (parsedKotlinVersion.compareTo(min) >= 0 && parsedKotlinVersion.compareTo(max) <= 0) {
                                            ideKotlinVersions.add(parsedVersion);
                                        }
                                    }
                                }

                                onFinish.accept(ideKotlinVersions);
                            });
                });
    }

    private void fillVersions() {
        languageVersionComboBox.addItem(VersionView.LatestStable.INSTANCE);
        apiVersionComboBox.addItem(VersionView.LatestStable.INSTANCE);

        if (isProjectSettings && jpsPluginSettings != null) {
            defaultJpsVersionItem = JpsVersionItem.createFromRawVersion(
                    KotlinJpsPluginSettingsKt.getVersionWithFallback(jpsPluginSettings)
            );

            kotlinJpsPluginVersionComboBox.addItem(defaultJpsVersionItem);

            IdeKotlinVersion bundledVersion = KotlinJpsPluginSettings.getBundledVersion();
            IdeKotlinVersion defaultVersion = defaultJpsVersionItem.getVersion();
            Integer compare = defaultVersion != null ? defaultVersion.compareTo(bundledVersion) : null;
            if (compare == null || compare > 0) {
                jpsPluginComboBoxModel.add(new JpsVersionItem(bundledVersion));
            } else if (compare < 0) {
                jpsPluginComboBoxModel.add(0, new JpsVersionItem(bundledVersion));
            }

            JpsVersionItem loadingItem = JpsVersionItem.createLabel(KotlinBaseCompilerConfigurationUiBundle.message("loading.available.versions.from.maven"));
            kotlinJpsPluginVersionComboBox.addItem(loadingItem);
            PopupMenuListenerAdapter popupListener = new PopupMenuListenerAdapter() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    kotlinJpsPluginVersionComboBox.removePopupMenuListener(this);
                    fetchAvailableJpsCompilersAsync(
                            availableVersions -> {
                                kotlinJpsPluginVersionComboBox.removeItem(loadingItem);
                                if (availableVersions == null) {
                                    kotlinJpsPluginVersionComboBox.addItem(
                                            JpsVersionItem.createLabel(
                                                    KotlinBaseCompilerConfigurationUiBundle.message("failed.fetching.all.available.versions.from.maven")
                                            )
                                    );
                                } else {
                                    SortedSet<IdeKotlinVersion> newItems = new TreeSet<>(availableVersions);
                                    for (JpsVersionItem item : jpsPluginComboBoxModel.getItems()) {
                                        IdeKotlinVersion ideKotlinVersion = item.getVersion();
                                        if (ideKotlinVersion != null) {
                                            newItems.add(ideKotlinVersion);
                                        }
                                    }

                                    Object selectedItem = jpsPluginComboBoxModel.getSelectedItem();
                                    jpsPluginComboBoxModel.update(
                                            ContainerUtil.reverse(ContainerUtil.map(newItems, it -> new JpsVersionItem(it))));
                                    kotlinJpsPluginVersionComboBox.setSelectedItem(selectedItem);
                                }

                                if (kotlinJpsPluginVersionComboBox.isPopupVisible()) {
                                    kotlinJpsPluginVersionComboBox.hidePopup();
                                    kotlinJpsPluginVersionComboBox.showPopup();
                                }
                            });
                }
            };

            kotlinJpsPluginVersionComboBox.addPopupMenuListener(popupListener);
        } else {
            kotlinJpsPluginVersionPanel.setVisible(false);
        }

        for (LanguageVersion languageVersion : LanguageVersion.values()) {
            if (!LanguageVersionSettingsKt.isStableOrReadyForPreview(languageVersion)) {
                continue;
            }

            ApiVersion apiVersion = ApiVersion.createByLanguageVersion(languageVersion);

            if (!apiVersion.isUnsupported()) {
                apiVersionComboBox.addItem(new VersionView.Specific(languageVersion));
            }
            if (!languageVersion.isUnsupported()) {
                languageVersionComboBox.addItem(new VersionView.Specific(languageVersion));
            }
        }
        languageVersionComboBox.setRenderer(new DescriptionListCellRenderer());
        kotlinJpsPluginVersionComboBox.setRenderer(new DescriptionListCellRenderer());
        apiVersionComboBox.setRenderer(new DescriptionListCellRenderer());
    }

    public void setTargetPlatform(@Nullable IdePlatformKind targetPlatform) {
        k2jsPanel.setVisible(JsIdePlatformUtil.isJavaScript(targetPlatform));
        scriptPanel.setVisible(JvmIdePlatformUtil.isJvm(targetPlatform));
    }

    private void fillModuleKindList() {
        for (@Nls String moduleKind : moduleKindDescriptions.keySet()) {
            moduleKindComboBox.addItem(moduleKind);
        }

        moduleKindComboBox.setRenderer(SimpleListCellRenderer.create("", o -> getModuleKindDescription(o)));
    }

    private void fillSourceMapSourceEmbeddingList() {
        for (@Nls String moduleKind : sourceMapSourceEmbeddingDescriptions.keySet()) {
            sourceMapEmbedSources.addItem(moduleKind);
        }

        sourceMapEmbedSources.setRenderer(SimpleListCellRenderer.create("", o -> getSourceMapSourceEmbeddingDescription(o)));
    }

    @NotNull
    @Override
    public String getId() {
        return "project.kotlinCompiler";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return contentPane;
    }

    @Override
    public boolean isModified() {
        return isCheckboxModified(reportWarningsCheckBox, !commonCompilerArguments.getSuppressWarnings()) ||
               !getSelectedLanguageVersionView().equals(KotlinFacetSettingsKt.getLanguageVersionView(commonCompilerArguments)) ||
               !getSelectedAPIVersionView().equals(KotlinFacetSettingsKt.getApiVersionView(commonCompilerArguments)) ||
               jpsPluginSettings != null &&
               !getSelectedKotlinJpsPluginVersion().equals(KotlinJpsPluginSettingsKt.getVersionWithFallback(jpsPluginSettings)) ||
               !additionalArgsOptionsField.getText().equals(compilerSettings.getAdditionalArguments()) ||
               isFieldModified(scriptTemplatesField, compilerSettings.getScriptTemplates()) ||
               isFieldModified(scriptTemplatesClasspathField, compilerSettings.getScriptTemplatesClasspath()) ||
               isCheckboxModified(copyRuntimeFilesCheckBox, compilerSettings.getCopyJsLibraryFiles()) ||
               isBrowseFieldModified(outputDirectory, compilerSettings.getOutputDirectoryForJsLibraryFiles()) ||

               (compilerWorkspaceSettings != null &&
                (isCheckboxModified(enableIncrementalCompilationForJvmCheckBox, compilerWorkspaceSettings.getPreciseIncrementalEnabled()) ||
                 isCheckboxModified(enableIncrementalCompilationForJsCheckBox, compilerWorkspaceSettings.getIncrementalCompilationForJsEnabled()) ||
                 isCheckboxModified(keepAliveCheckBox, compilerWorkspaceSettings.getEnableDaemon()))) ||

               isCheckboxModified(generateSourceMapsCheckBox, k2jsCompilerArguments.getSourceMap()) ||
               isBrowseFieldModifiedWithNullize(outputPrefixFile, k2jsCompilerArguments.getOutputPrefix()) ||
               isBrowseFieldModifiedWithNullize(outputPostfixFile, k2jsCompilerArguments.getOutputPostfix()) ||
               !getSelectedModuleKind().equals(getModuleKindOrDefault(k2jsCompilerArguments.getModuleKind())) ||
               isFieldModified(sourceMapPrefix, StringUtil.notNullize(k2jsCompilerArguments.getSourceMapPrefix())) ||
               !getSelectedSourceMapSourceEmbedding().equals(
                       getSourceMapSourceEmbeddingOrDefault(k2jsCompilerArguments.getSourceMapEmbedSources())) ||
               !getSelectedJvmVersion().equals(getJvmVersionOrDefault(k2jvmCompilerArguments.getJvmTarget()));
    }

    @NotNull
    private String getSelectedModuleKind() {
        return getModuleKindOrDefault((String) moduleKindComboBox.getSelectedItem());
    }

    private String getSelectedSourceMapSourceEmbedding() {
        return getSourceMapSourceEmbeddingOrDefault((String) sourceMapEmbedSources.getSelectedItem());
    }

    @NotNull
    public String getSelectedJvmVersion() {
        return getJvmVersionOrDefault((String) jvmVersionComboBox.getSelectedItem());
    }

    @NotNull
    public VersionView getSelectedLanguageVersionView() {
        Object item = languageVersionComboBox.getSelectedItem();
        return item != null ? (VersionView) item : VersionView.LatestStable.INSTANCE;
    }

    @NotNull
    private VersionView getSelectedAPIVersionView() {
        Object item = apiVersionComboBox.getSelectedItem();
        return item != null ? (VersionView) item : VersionView.LatestStable.INSTANCE;
    }

    private @NotNull String getSelectedKotlinJpsPluginVersion() {
        JpsVersionItem item = (JpsVersionItem) kotlinJpsPluginVersionComboBox.getSelectedItem();
        return normalizeKotlinJpsPluginVersion(item != null ? item.getRawVersion() : null);
    }

    private static @NlsSafe @NotNull String normalizeKotlinJpsPluginVersion(@Nullable String version) {
        if (version != null && !version.isEmpty()) {
            return version;
        }

        return KotlinJpsPluginSettings.getRawBundledVersion();
    }

    public void applyTo(
            CommonCompilerArguments commonCompilerArguments,
            K2JVMCompilerArguments k2jvmCompilerArguments,
            K2JSCompilerArguments k2jsCompilerArguments,
            CompilerSettings compilerSettings
    ) throws ConfigurationException {
        if (isProjectSettings) {
            boolean shouldInvalidateCaches =
                    !getSelectedLanguageVersionView().equals(KotlinFacetSettingsKt.getLanguageVersionView(commonCompilerArguments)) ||
                    !getSelectedAPIVersionView().equals(KotlinFacetSettingsKt.getApiVersionView(commonCompilerArguments)) ||
                    jpsPluginSettings != null &&
                    !getSelectedKotlinJpsPluginVersion().equals(KotlinJpsPluginSettingsKt.getVersionWithFallback(jpsPluginSettings)) ||
                    !additionalArgsOptionsField.getText().equals(compilerSettings.getAdditionalArguments());

            if (shouldInvalidateCaches) {
                ApplicationUtilsKt.runWriteAction(
                        new Function0<>() {
                            @Override
                            public Object invoke() {
                                ProjectStructureUtils.invalidateProjectRoots(project, RootsChangeRescanningInfo.NO_RESCAN_NEEDED);
                                return null;
                            }
                        }
                );
            }
        }

        commonCompilerArguments.setSuppressWarnings(!reportWarningsCheckBox.isSelected());
        KotlinFacetSettingsKt.setLanguageVersionView(commonCompilerArguments, getSelectedLanguageVersionView());
        KotlinFacetSettingsKt.setApiVersionView(commonCompilerArguments, getSelectedAPIVersionView());

        compilerSettings.setAdditionalArguments(additionalArgsOptionsField.getText());
        compilerSettings.setScriptTemplates(scriptTemplatesField.getText());
        compilerSettings.setScriptTemplatesClasspath(scriptTemplatesClasspathField.getText());
        compilerSettings.setCopyJsLibraryFiles(copyRuntimeFilesCheckBox.isSelected());
        compilerSettings.setOutputDirectoryForJsLibraryFiles(outputDirectory.getText());

        if (compilerWorkspaceSettings != null) {
            compilerWorkspaceSettings.setPreciseIncrementalEnabled(enableIncrementalCompilationForJvmCheckBox.isSelected());
            compilerWorkspaceSettings.setIncrementalCompilationForJsEnabled(enableIncrementalCompilationForJsCheckBox.isSelected());

            boolean oldEnableDaemon = compilerWorkspaceSettings.getEnableDaemon();
            compilerWorkspaceSettings.setEnableDaemon(keepAliveCheckBox.isSelected());
            if (keepAliveCheckBox.isSelected() != oldEnableDaemon) {
                PluginStartupApplicationService.getInstance().resetAliveFlag();
            }
        }

        k2jsCompilerArguments.setSourceMap(generateSourceMapsCheckBox.isSelected());
        k2jsCompilerArguments.setOutputPrefix(StringUtil.nullize(outputPrefixFile.getText(), true));
        k2jsCompilerArguments.setOutputPostfix(StringUtil.nullize(outputPostfixFile.getText(), true));
        k2jsCompilerArguments.setModuleKind(getSelectedModuleKind());

        k2jsCompilerArguments.setSourceMapPrefix(sourceMapPrefix.getText());
        k2jsCompilerArguments
                .setSourceMapEmbedSources(generateSourceMapsCheckBox.isSelected() ? getSelectedSourceMapSourceEmbedding() : null);

        k2jvmCompilerArguments.setJvmTarget(getSelectedJvmVersion());

        if (isProjectSettings) {
            if (jpsPluginSettings != null) {
                String jpsPluginVersion = getSelectedKotlinJpsPluginVersion();
                if (!jpsPluginSettings.getVersion().isEmpty() ||
                    !jpsPluginVersion.equals(KotlinJpsPluginSettingsKt.getVersionWithFallback(jpsPluginSettings))) {
                    defaultJpsVersionItem = (JpsVersionItem) kotlinJpsPluginVersionComboBox.getSelectedItem();
                    jpsPluginSettings.setVersion(jpsPluginVersion);
                    KotlinJpsPluginSettings.getInstance(project).setSettings(jpsPluginSettings);
                }
            }

            KotlinCommonCompilerArgumentsHolder.getInstance(project).setSettings(commonCompilerArguments);
            Kotlin2JvmCompilerArgumentsHolder.getInstance(project).setSettings(k2jvmCompilerArguments);
            Kotlin2JsCompilerArgumentsHolder.getInstance(project).setSettings(k2jsCompilerArguments);
            KotlinCompilerSettings.getInstance(project).setSettings(compilerSettings);
        }

        BuildManager.getInstance().clearState(project);
    }

    @Override
    public void apply() throws ConfigurationException {
        applyTo(commonCompilerArguments, k2jvmCompilerArguments, k2jsCompilerArguments, compilerSettings);
    }

    @Override
    public void reset() {
        reportWarningsCheckBox.setSelected(!commonCompilerArguments.getSuppressWarnings());
        if (jpsPluginSettings != null) {
            setSelectedItem(kotlinJpsPluginVersionComboBox, defaultJpsVersionItem);
        }
        setSelectedItem(languageVersionComboBox, KotlinFacetSettingsKt.getLanguageVersionView(commonCompilerArguments));
        onLanguageLevelChanged((VersionView) languageVersionComboBox.getSelectedItem()); // getSelectedLanguageVersionView() replaces null
        setSelectedItem(apiVersionComboBox, KotlinFacetSettingsKt.getApiVersionView(commonCompilerArguments));
        additionalArgsOptionsField.setText(compilerSettings.getAdditionalArguments());
        scriptTemplatesField.setText(compilerSettings.getScriptTemplates());
        scriptTemplatesClasspathField.setText(compilerSettings.getScriptTemplatesClasspath());
        copyRuntimeFilesCheckBox.setSelected(compilerSettings.getCopyJsLibraryFiles());
        outputDirectory.setText(compilerSettings.getOutputDirectoryForJsLibraryFiles());

        if (compilerWorkspaceSettings != null) {
            enableIncrementalCompilationForJvmCheckBox.setSelected(compilerWorkspaceSettings.getPreciseIncrementalEnabled());
            enableIncrementalCompilationForJsCheckBox.setSelected(compilerWorkspaceSettings.getIncrementalCompilationForJsEnabled());
            keepAliveCheckBox.setSelected(compilerWorkspaceSettings.getEnableDaemon());
        }

        generateSourceMapsCheckBox.setSelected(k2jsCompilerArguments.getSourceMap());
        outputPrefixFile.setText(k2jsCompilerArguments.getOutputPrefix());
        outputPostfixFile.setText(k2jsCompilerArguments.getOutputPostfix());

        moduleKindComboBox.setSelectedItem(getModuleKindOrDefault(k2jsCompilerArguments.getModuleKind()));
        sourceMapPrefix.setText(k2jsCompilerArguments.getSourceMapPrefix());
        sourceMapPrefix.setEnabled(k2jsCompilerArguments.getSourceMap());
        sourceMapEmbedSources.setSelectedItem(getSourceMapSourceEmbeddingOrDefault(k2jsCompilerArguments.getSourceMapEmbedSources()));

        jvmVersionComboBox.setSelectedItem(getJvmVersionOrDefault(k2jvmCompilerArguments.getJvmTarget()));
    }

    private static <T> void setSelectedItem(JComboBox<T> comboBox, T versionView) {
        // Imported projects might have outdated language/api versions - we display them as well (see createVersionValidator() for details)
        int index = ((MutableCollectionComboBoxModel<T>) comboBox.getModel()).getElementIndex(versionView);
        if (index == -1) {
            comboBox.addItem(versionView);
        }

        comboBox.setSelectedItem(versionView);
    }

    @Override
    public void disposeUIResources() {
        Disposer.dispose(this);
    }

    @Nls
    @Override
    public String getDisplayName() {
        return KotlinBaseCompilerConfigurationUiBundle.message("configuration.name.kotlin.compiler");
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return "reference.compiler.kotlin";
    }

    public JPanel getContentPane() {
        return contentPane;
    }

    public ThreeStateCheckBox getReportWarningsCheckBox() {
        return reportWarningsCheckBox;
    }

    public RawCommandLineEditor getAdditionalArgsOptionsField() {
        return additionalArgsOptionsField;
    }

    public ThreeStateCheckBox getGenerateSourceMapsCheckBox() {
        return generateSourceMapsCheckBox;
    }

    public TextFieldWithBrowseButton getOutputPrefixFile() {
        return outputPrefixFile;
    }

    public TextFieldWithBrowseButton getOutputPostfixFile() {
        return outputPostfixFile;
    }

    public TextFieldWithBrowseButton getOutputDirectory() {
        return outputDirectory;
    }

    public ThreeStateCheckBox getCopyRuntimeFilesCheckBox() {
        return copyRuntimeFilesCheckBox;
    }

    public ThreeStateCheckBox getKeepAliveCheckBox() {
        return keepAliveCheckBox;
    }

    public JComboBox<String> getModuleKindComboBox() {
        return moduleKindComboBox;
    }

    public JTextField getScriptTemplatesField() {
        return scriptTemplatesField;
    }

    public JTextField getScriptTemplatesClasspathField() {
        return scriptTemplatesClasspathField;
    }

    public JComboBox<VersionView> getLanguageVersionComboBox() {
        return languageVersionComboBox;
    }

    public JComboBox<VersionView> getApiVersionComboBox() {
        return apiVersionComboBox;
    }

    public void setEnabled(boolean value) {
        isEnabled = value;
        UIUtil.setEnabled(getContentPane(), value, true);
    }

    public CommonCompilerArguments getCommonCompilerArguments() {
        return commonCompilerArguments;
    }

    public void setCommonCompilerArguments(CommonCompilerArguments commonCompilerArguments) {
        this.commonCompilerArguments = commonCompilerArguments;
    }

    public K2JSCompilerArguments getK2jsCompilerArguments() {
        return k2jsCompilerArguments;
    }

    public void setK2jsCompilerArguments(K2JSCompilerArguments k2jsCompilerArguments) {
        this.k2jsCompilerArguments = k2jsCompilerArguments;
    }

    public K2JVMCompilerArguments getK2jvmCompilerArguments() {
        return k2jvmCompilerArguments;
    }

    public void setK2jvmCompilerArguments(K2JVMCompilerArguments k2jvmCompilerArguments) {
        this.k2jvmCompilerArguments = k2jvmCompilerArguments;
    }

    public CompilerSettings getCompilerSettings() {
        return compilerSettings;
    }

    public void setCompilerSettings(CompilerSettings compilerSettings) {
        this.compilerSettings = compilerSettings;
    }

    private static class ComboBoxModelWithPossiblyDisabledItems extends MutableCollectionComboBoxModel<JpsVersionItem> {
        @Override
        public void setSelectedItem(@Nullable Object item) {
            if (item == null) return;

            if (!(item instanceof JpsVersionItem)) {
                throw new IllegalStateException(item + "is supposed to be JpsVersionItem");
            }

            if (!((JpsVersionItem) item).myEnabled) {
                return;
            }
            super.setSelectedItem(item);
        }
    }

    private void createUIComponents() {
        // Explicit use of MutableCollectionComboBoxModel guarantees that setSelectedItem() can make safe cast.
        languageVersionComboBox = new ComboBox<>(new MutableCollectionComboBoxModel<>());
        jpsPluginComboBoxModel = new ComboBoxModelWithPossiblyDisabledItems();
        kotlinJpsPluginVersionComboBox = new ComboBox<>(jpsPluginComboBoxModel);
        apiVersionComboBox = new ComboBox<>(new MutableCollectionComboBoxModel<>());

        createVersionValidator(languageVersionComboBox, "configuration.warning.text.language.version.unsupported");
        createVersionValidator(apiVersionComboBox, "configuration.warning.text.api.version.unsupported");

        // Workaround: ThreeStateCheckBox doesn't send suitable notification on state change
        // TODO: replace with PropertyChangerListener after fix is available in IDEA
        copyRuntimeFilesCheckBox = new ThreeStateCheckBox() {
            @Override
            public void setState(State state) {
                super.setState(state);
                updateOutputDirEnabled();
            }
        };
    }

    private void createVersionValidator(JComboBox<VersionView> component, String messageKey) {
        new ComponentValidator(this)
                .withValidator(() -> {
                    VersionView selectedItem = (VersionView) component.getSelectedItem();
                    if (selectedItem == null) return null;

                    LanguageOrApiVersion version = selectedItem.getVersion();
                    if (version.isUnsupported()) {
                        return new ValidationInfo(KotlinBaseCompilerConfigurationUiBundle.message(messageKey, version.getVersionString()), component);
                    }

                    return null;
                }).installOn(component);
        component.addActionListener(e -> ComponentValidator.getInstance(component).ifPresent(ComponentValidator::revalidate));
    }
}
