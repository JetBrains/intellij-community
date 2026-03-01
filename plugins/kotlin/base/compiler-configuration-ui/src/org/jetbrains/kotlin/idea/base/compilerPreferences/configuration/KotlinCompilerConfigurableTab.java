// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.compilerPreferences.configuration;

import com.intellij.compiler.server.BuildManager;
import com.intellij.icons.AllIcons;
import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.MutableCollectionComboBoxModel;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import kotlin.KotlinVersion;
import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.enums.EnumEntries;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.FreezableKt;
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.K2JsArgumentConstants;
import org.jetbrains.kotlin.config.ApiVersion;
import org.jetbrains.kotlin.config.CompilerSettings;
import org.jetbrains.kotlin.config.IKotlinFacetSettings;
import org.jetbrains.kotlin.config.JpsPluginSettings;
import org.jetbrains.kotlin.config.JvmTarget;
import org.jetbrains.kotlin.config.KotlinFacetSettingsKt;
import org.jetbrains.kotlin.config.LanguageOrApiVersion;
import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.config.VersionView;
import org.jetbrains.kotlin.idea.PluginStartupApplicationService;
import org.jetbrains.kotlin.idea.base.compilerPreferences.KotlinBaseCompilerConfigurationUiBundle;
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants;
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils;
import org.jetbrains.kotlin.idea.base.util.ProjectStructureUtils;
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion;
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JsCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerWorkspaceSettings;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettingsKt;
import org.jetbrains.kotlin.idea.facet.KotlinFacet;
import org.jetbrains.kotlin.idea.util.application.ApplicationUtilsKt;
import org.jetbrains.kotlin.platform.IdePlatformKind;
import org.jetbrains.kotlin.platform.PlatformUtilKt;
import org.jetbrains.kotlin.platform.TargetPlatform;
import org.jetbrains.kotlin.platform.impl.JsIdePlatformUtil;
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind;
import org.jetbrains.kotlin.platform.jvm.JdkPlatform;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.event.PopupMenuEvent;
import java.awt.Dimension;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.options.Configurable.isCheckboxModified;
import static com.intellij.openapi.options.Configurable.isFieldModified;
import static org.jetbrains.kotlin.idea.base.compilerPreferences.facet.DescriptionListCellRendererKt.createDescriptionAwareRenderer;

public class KotlinCompilerConfigurableTab implements SearchableConfigurable {
  private static final Logger LOG = Logger.getInstance(KotlinCompilerConfigurableTab.class);
  private static final Map<String, @NlsSafe String> moduleKindDescriptions = new LinkedHashMap<>();
  private static final Map<String, @NlsSafe String> sourceMapSourceEmbeddingDescriptions = new LinkedHashMap<>();
  private static final int MAX_WARNING_SIZE = 75;

  static {
    moduleKindDescriptions.put(K2JsArgumentConstants.MODULE_PLAIN,
                               KotlinBaseCompilerConfigurationUiBundle.message("configuration.description.plain.put.to.global.scope"));
    moduleKindDescriptions.put(K2JsArgumentConstants.MODULE_AMD,
                               KotlinBaseCompilerConfigurationUiBundle.message("configuration.description.amd"));
    moduleKindDescriptions.put(K2JsArgumentConstants.MODULE_COMMONJS,
                               KotlinBaseCompilerConfigurationUiBundle.message("configuration.description.commonjs"));
    moduleKindDescriptions.put(K2JsArgumentConstants.MODULE_UMD, KotlinBaseCompilerConfigurationUiBundle.message(
      "configuration.description.umd.detect.amd.or.commonjs.if.available.fallback.to.plain"));

    sourceMapSourceEmbeddingDescriptions
      .put(K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_NEVER,
           KotlinBaseCompilerConfigurationUiBundle.message("configuration.description.never"));
    sourceMapSourceEmbeddingDescriptions
      .put(K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_ALWAYS,
           KotlinBaseCompilerConfigurationUiBundle.message("configuration.description.always"));
    sourceMapSourceEmbeddingDescriptions.put(K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_INLINING,
                                             KotlinBaseCompilerConfigurationUiBundle.message(
                                               "configuration.description.when.inlining.a.function.from.other.module.with.embedded.sources"));
  }

  private final @Nullable KotlinCompilerWorkspaceSettings compilerWorkspaceSettings;
  private final Project project;
  private final boolean isProjectSettings;
  private CommonCompilerArguments commonCompilerArguments;
  private K2JSCompilerArguments k2jsCompilerArguments;
  private K2JVMCompilerArguments k2jvmCompilerArguments;
  private CompilerSettings compilerSettings;
  private final @Nullable JpsPluginSettings jpsPluginSettings;
  private final JPanel contentPane;
  private final ThreeStateCheckBox reportWarningsCheckBox;
  private final RawCommandLineEditor additionalArgsOptionsField;
  private final JLabel additionalArgsLabel;
  private final ThreeStateCheckBox generateSourceMapsCheckBox;
  private final JLabel labelForOutputDirectory;
  private final TextFieldWithBrowseButton outputDirectory;
  private final ThreeStateCheckBox copyRuntimeFilesCheckBox;
  private final ThreeStateCheckBox keepAliveCheckBox;
  private final JCheckBox enableIncrementalCompilationForJvmCheckBox;
  private final JCheckBox enableIncrementalCompilationForJsCheckBox;
  private final JComboBox<String> moduleKindComboBox;
  private final JPanel k2jvmPanel;
  private final JPanel k2jsPanel;
  private final JComboBox<String> jvmVersionComboBox;
  private final JPanel kotlinJpsPluginVersionPanel;
  private final JComboBox<JpsVersionItem> kotlinJpsPluginVersionComboBox;
  private ComboBoxModelWithPossiblyDisabledItems jpsPluginComboBoxModel;
  private JpsVersionItem defaultJpsVersionItem;
  private final JComboBox<VersionView> languageVersionComboBox;
  private final JComboBox<VersionView> apiVersionComboBox;
  private final JLabel warningLabel;
  private final JTextField sourceMapPrefix;
  private final JComboBox<String> sourceMapEmbedSources;
  private boolean isEnabled = true;

  private @Nullable Disposable validatorsDisposable = null;

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
    {
      // Explicit use of MutableCollectionComboBoxModel guarantees that setSelectedItem() can make safe cast.
      languageVersionComboBox = new ComboBox<>(new MutableCollectionComboBoxModel<>());
      jpsPluginComboBoxModel = new ComboBoxModelWithPossiblyDisabledItems();
      kotlinJpsPluginVersionComboBox = new ComboBox<>(jpsPluginComboBoxModel);
      apiVersionComboBox = new ComboBox<>(new MutableCollectionComboBoxModel<>());

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
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      contentPane = new JPanel();
      contentPane.setLayout(new GridLayoutManager(12, 2, new Insets(0, 0, 0, 0), -1, -1));
      contentPane.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                         TitledBorder.DEFAULT_POSITION, null, null));
      final Spacer spacer1 = new Spacer();
      contentPane.add(spacer1, new GridConstraints(11, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                   GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      k2jsPanel = new JPanel();
      k2jsPanel.setLayout(new GridLayoutManager(7, 2, new Insets(0, 0, 0, 0), -1, -1));
      k2jsPanel.setVisible(true);
      k2jsPanel.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithoutIndent");
      contentPane.add(k2jsPanel, new GridConstraints(9, 0, 1, 2, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null, null, 0, true));
      k2jsPanel.setBorder(IdeBorderFactory.PlainSmallWithoutIndent.createTitledBorder(BorderFactory.createEtchedBorder(),
                                                                                      this.$$$getMessageFromBundle$$$(
                                                                                        "messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                                        "kotlin.compiler.js.option.panel.title"),
                                                                                      TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                      TitledBorder.DEFAULT_POSITION, null, null));
      labelForOutputDirectory = new JLabel();
      this.$$$loadLabelText$$$(labelForOutputDirectory, this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                                        "destination.directory"));
      k2jsPanel.add(labelForOutputDirectory, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                                 null, null, 2, false));
      outputDirectory = new TextFieldWithBrowseButton();
      outputDirectory.setText(this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle", "kotlin.compiler.lib"));
      k2jsPanel.add(outputDirectory, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                         new Dimension(150, -1), null, 0, false));
      copyRuntimeFilesCheckBox.setState(ThreeStateCheckBox.State.NOT_SELECTED);
      this.$$$loadButtonText$$$(copyRuntimeFilesCheckBox,
                                this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                "kotlin.compiler.js.option.output.copy.files"));
      k2jsPanel.add(copyRuntimeFilesCheckBox, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                  null, null, null, 0, false));
      final JLabel label1 = new JLabel();
      this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle", "module.kind"));
      k2jsPanel.add(label1, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                false));
      moduleKindComboBox = new JComboBox();
      final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
      moduleKindComboBox.setModel(defaultComboBoxModel1);
      k2jsPanel.add(moduleKindComboBox, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                            null, null, 0, false));
      generateSourceMapsCheckBox = new ThreeStateCheckBox();
      generateSourceMapsCheckBox.setState(ThreeStateCheckBox.State.NOT_SELECTED);
      this.$$$loadButtonText$$$(generateSourceMapsCheckBox,
                                this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                "kotlin.compiler.js.option.generate.sourcemaps"));
      k2jsPanel.add(generateSourceMapsCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
      sourceMapPrefix = new JTextField();
      k2jsPanel.add(sourceMapPrefix, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                         new Dimension(150, -1), null, 0, false));
      final JLabel label2 = new JLabel();
      this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                       "embed.source.code.into.source.map"));
      k2jsPanel.add(label2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                false));
      sourceMapEmbedSources = new JComboBox();
      k2jsPanel.add(sourceMapEmbedSources, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null, null, 0, false));
      enableIncrementalCompilationForJsCheckBox = new JCheckBox();
      this.$$$loadButtonText$$$(enableIncrementalCompilationForJsCheckBox,
                                this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                "enable.incremental.compilation"));
      k2jsPanel.add(enableIncrementalCompilationForJsCheckBox,
                    new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      contentPane.add(panel1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, false));
      warningLabel = new JLabel();
      warningLabel.setFocusable(false);
      warningLabel.setInheritsPopupMenu(false);
      warningLabel.setText("");
      warningLabel.setVisible(false);
      panel1.add(warningLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final Spacer spacer2 = new Spacer();
      panel1.add(spacer2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      k2jvmPanel = new JPanel();
      k2jvmPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
      k2jvmPanel.setVisible(true);
      k2jvmPanel.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithoutIndent");
      contentPane.add(k2jvmPanel, new GridConstraints(8, 0, 1, 2, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null, null, 0, true));
      k2jvmPanel.setBorder(IdeBorderFactory.PlainSmallWithoutIndent.createTitledBorder(BorderFactory.createEtchedBorder(),
                                                                                       this.$$$getMessageFromBundle$$$(
                                                                                         "messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                                         "kotlin.compiler.jvm.option.panel.title"),
                                                                                       TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                       TitledBorder.DEFAULT_POSITION, null, null));
      final JLabel label3 = new JLabel();
      this.$$$loadLabelText$$$(label3,
                               this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle", "target.jvm.version"));
      k2jvmPanel.add(label3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                 false));
      enableIncrementalCompilationForJvmCheckBox = new JCheckBox();
      this.$$$loadButtonText$$$(enableIncrementalCompilationForJvmCheckBox,
                                this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                "enable.incremental.compilation"));
      k2jvmPanel.add(enableIncrementalCompilationForJvmCheckBox,
                     new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      jvmVersionComboBox = new JComboBox();
      k2jvmPanel.add(jvmVersionComboBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                             null, null, 0, false));
      final JPanel panel2 = new JPanel();
      panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
      contentPane.add(panel2, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, true));
      reportWarningsCheckBox = new ThreeStateCheckBox();
      reportWarningsCheckBox.setSelected(false);
      reportWarningsCheckBox.setState(ThreeStateCheckBox.State.NOT_SELECTED);
      this.$$$loadButtonText$$$(reportWarningsCheckBox, this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                                        "kotlin.compiler.option.generate.no.warnings"));
      panel2.add(reportWarningsCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      kotlinJpsPluginVersionPanel = new JPanel();
      kotlinJpsPluginVersionPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      contentPane.add(kotlinJpsPluginVersionPanel, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                       GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                       GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
      final JLabel label4 = new JLabel();
      this.$$$loadLabelText$$$(label4, this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                       "kotlin.compiler.version"));
      kotlinJpsPluginVersionPanel.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                                  null, null, 0, false));
      kotlinJpsPluginVersionPanel.add(kotlinJpsPluginVersionComboBox,
                                      new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                          null, 0, false));
      final JPanel panel3 = new JPanel();
      panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      contentPane.add(panel3, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, true));
      final JLabel label5 = new JLabel();
      this.$$$loadLabelText$$$(label5,
                               this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle", "language.version"));
      panel3.add(label5,
                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      panel3.add(languageVersionComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
      final JPanel panel4 = new JPanel();
      panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      contentPane.add(panel4, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, true));
      final JLabel label6 = new JLabel();
      this.$$$loadLabelText$$$(label6, this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle", "api.version"));
      panel4.add(label6,
                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      panel4.add(apiVersionComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                         null, 0, false));
      additionalArgsLabel = new JLabel();
      this.$$$loadLabelText$$$(additionalArgsLabel, this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                                    "kotlin.compiler.option.additional.command.line.parameters"));
      contentPane.add(additionalArgsLabel, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null, null, 0, false));
      additionalArgsOptionsField = new RawCommandLineEditor();
      additionalArgsOptionsField.setDialogCaption(
        this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle", "additional.command.line.parameters"));
      contentPane.add(additionalArgsOptionsField,
                      new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      keepAliveCheckBox = new ThreeStateCheckBox();
      keepAliveCheckBox.setState(ThreeStateCheckBox.State.NOT_SELECTED);
      this.$$$loadButtonText$$$(keepAliveCheckBox, this.$$$getMessageFromBundle$$$("messages/KotlinBaseCompilerConfigurationUiBundle",
                                                                                   "keep.compiler.process.alive.between.invocations"));
      contentPane.add(keepAliveCheckBox, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      label1.setLabelFor(moduleKindComboBox);
    }
    this.jpsPluginSettings = Optional.ofNullable(isProjectSettings ? KotlinJpsPluginSettings.getInstance(project) : null)
      .map(KotlinJpsPluginSettings::getSettings)
      .map(FreezableKt::unfrozen)
      .orElse(null);
    this.compilerWorkspaceSettings = compilerWorkspaceSettings;
    this.k2jvmCompilerArguments = k2jvmCompilerArguments;
    this.isProjectSettings = isProjectSettings;

    warningLabel.setIcon(AllIcons.General.WarningDialog);

    additionalArgsOptionsField.attachLabel(additionalArgsLabel);
    if (isJpsCompilerVisible()) {
      kotlinJpsPluginVersionComboBox.addActionListener(
        e -> onLanguageLevelChanged(getSelectedKotlinJpsPluginVersionView()));
    }

    fillVersions();

    if (KotlinPlatformUtils.isCidr()) {
      keepAliveCheckBox.setVisible(false);
      k2jvmPanel.setVisible(false);
      k2jsPanel.setVisible(false);
    }
    else {
      initializeNonCidrSettings(isMultiEditor);
    }

    reportWarningsCheckBox.setThirdStateEnabled(isMultiEditor);

    if (isProjectSettings) {
      List<String> modulesOverridingProjectSettings = ArraysKt.mapNotNull(
        ModuleManager.getInstance(project).getModules(),
        module -> {
          KotlinFacet facet = KotlinFacet.Companion.get(module);
          if (facet == null) return null;
          IKotlinFacetSettings facetSettings = facet.getConfiguration().getSettings();
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

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return contentPane; }

  private void initializeNonCidrSettings(boolean isMultiEditor) {
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

  private static int calculateNameCountToShowInWarning(List<String> allNames) {
    int lengthSoFar = 0;
    int size = allNames.size();
    for (int i = 0; i < size; i++) {
      lengthSoFar = (i > 0 ? lengthSoFar + 2 : 0) + allNames.get(i).length();
      if (lengthSoFar > MAX_WARNING_SIZE) return i;
    }
    return size;
  }

  private static @NotNull @NlsSafe String buildOverridingModulesWarning(List<String> modulesOverridingProjectSettings) {
    int nameCountToShow = calculateNameCountToShowInWarning(modulesOverridingProjectSettings);
    int allNamesCount = modulesOverridingProjectSettings.size();
    if (nameCountToShow == 0) {
      return KotlinBaseCompilerConfigurationUiBundle.message("configuration.warning.text.modules.override.project.settings",
                                                             String.valueOf(allNamesCount));
    }

    StringBuilder builder = new StringBuilder();
    builder.append("<html>");
    builder.append(
        KotlinBaseCompilerConfigurationUiBundle.message("configuration.warning.text.following.modules.override.project.settings"))
      .append(" ");
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
      builder.append(" ").append(KotlinBaseCompilerConfigurationUiBundle.message("configuration.text.and")).append(" ")
        .append(allNamesCount - nameCountToShow)
        .append(" ").append(KotlinBaseCompilerConfigurationUiBundle.message("configuration.text.other.s"));
    }
    return builder.toString();
  }

  @Nls
  private static @NotNull
  String getModuleKindDescription(@Nullable String moduleKind) {
    if (moduleKind == null) return "";
    String result = moduleKindDescriptions.get(moduleKind);
    assert result != null : "Module kind " + moduleKind + " was not added to combobox, therefore it should not be here";
    return result;
  }

  @Nls
  private static @NotNull
  String getSourceMapSourceEmbeddingDescription(@Nullable String sourceMapSourceEmbeddingId) {
    if (sourceMapSourceEmbeddingId == null) return "";
    String result = sourceMapSourceEmbeddingDescriptions.get(sourceMapSourceEmbeddingId);
    assert result != null : "Source map source embedding mode " + sourceMapSourceEmbeddingId +
                            " was not added to combobox, therefore it should not be here";
    return result;
  }

  private static @NotNull @NlsSafe String getModuleKindOrDefault(@Nullable String moduleKindId) {
    if (moduleKindId == null) {
      moduleKindId = K2JsArgumentConstants.MODULE_PLAIN;
    }
    return moduleKindId;
  }

  private static @NotNull @NlsSafe String getSourceMapSourceEmbeddingOrDefault(@Nullable String sourceMapSourceEmbeddingId) {
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
    var descriptor = new FileChooserDescriptor(forFiles, !forFiles, false, false, false, false).withTitle(title);
    fileChooser.addBrowseFolderListener(null, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
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
    EnumEntries<LanguageVersion> languageVersions = LanguageVersion.getEntries();
    List<VersionView> permittedAPIVersions = new ArrayList<>(languageVersions.size());

    final VersionView latestStable = getLatestStableVersion();

    int index = 0;
    int latestStableIndex = languageVersions.size();
    for (LanguageVersion version : languageVersions) {
      if (index > latestStableIndex) {
        break;
      }
      ApiVersion apiVersion = ApiVersion.createByLanguageVersion(version);
      if (!isLessOrEqual(apiVersion, upperBound) && (index < latestStableIndex)) {
        latestStableIndex = index;
      }
      if (!apiVersion.isUnsupported()) {
        permittedAPIVersions.add(new VersionView.Specific(version));
      }
      index++;
    }

    if (isLessOrEqual(latestStable.getVersion(), upperBound) && !permittedAPIVersions.contains(latestStable)) {
      permittedAPIVersions.add(latestStable);
    }

    apiVersionComboBox.setModel(new MutableCollectionComboBoxModel<>(permittedAPIVersions));
    languageVersionComboBox.setModel(new MutableCollectionComboBoxModel<>(permittedAPIVersions));

    VersionView selectedItem =
      VersionComparatorUtil.compare(selectedAPIVersion.getVersionString(), upperBound.getVersionString()) <= 0
      ? selectedAPIView
      : upperBoundView;
    apiVersionComboBox.setSelectedItem(selectedItem);
    if (isJpsCompilerVisible()) {
      languageVersionComboBox.setSelectedItem(selectedItem);
    }
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

  private boolean isJpsCompilerVisible() {
    return isProjectSettings && jpsPluginSettings != null;
  }

  private void fillVersions() {
    if (isJpsCompilerVisible()) {
      defaultJpsVersionItem = JpsVersionItem.createFromRawVersion(
        KotlinJpsPluginSettingsKt.getVersionWithFallback(jpsPluginSettings)
      );

      kotlinJpsPluginVersionComboBox.addItem(defaultJpsVersionItem);

      IdeKotlinVersion bundledVersion = KotlinJpsPluginSettings.getBundledVersion();
      IdeKotlinVersion defaultVersion = defaultJpsVersionItem.getVersion();
      Integer compare = defaultVersion != null ? defaultVersion.compareTo(bundledVersion) : null;
      if (compare == null || compare > 0) {
        jpsPluginComboBoxModel.add(new JpsVersionItem(bundledVersion));
      }
      else if (compare < 0) {
        jpsPluginComboBoxModel.add(0, new JpsVersionItem(bundledVersion));
      }

      JpsVersionItem loadingItem =
        JpsVersionItem.createLabel(KotlinBaseCompilerConfigurationUiBundle.message("loading.available.versions.from.maven"));
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
              }
              else {
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
    }
    else {
      kotlinJpsPluginVersionPanel.setVisible(false);
    }

    VersionView latestStable = getLatestStableVersion();
    int index = 0;
    EnumEntries<LanguageVersion> languageVersions = LanguageVersion.getEntries();
    int latestStableIndex = languageVersions.size();
    for (LanguageVersion languageVersion : languageVersions) {
      if (index > latestStableIndex) break;
      if (!isLessOrEqual(languageVersion, latestStable.getVersion()) && (index < latestStableIndex)) {
        latestStableIndex = index;
      }

      if (!languageVersion.isStable()) {
        continue;
      }

      ApiVersion apiVersion = ApiVersion.createByLanguageVersion(languageVersion);

      if (!apiVersion.isUnsupported()) {
        apiVersionComboBox.addItem(new VersionView.Specific(languageVersion));
      }
      if (!languageVersion.isUnsupported()) {
        languageVersionComboBox.addItem(new VersionView.Specific(languageVersion));
      }
      index++;
    }

    languageVersionComboBox.setRenderer(createDescriptionAwareRenderer());
    kotlinJpsPluginVersionComboBox.setRenderer(createDescriptionAwareRenderer());
    apiVersionComboBox.setRenderer(createDescriptionAwareRenderer());
  }

  private static VersionView latestStableVersion = null;

  private static VersionView getLatestStableVersion() {
    VersionView latestStable = latestStableVersion;
    if (latestStable != null) {
      return latestStable;
    }

    LanguageVersion bundledLanguageVersion = KotlinJpsPluginSettings.getBundledVersion().getLanguageVersion();
    latestStable = VersionView.LatestStable.INSTANCE;

    // workaround to avoid cases when Kotlin plugin bundles the latest compiler with effectively NOT STABLE version.
    // Actually, the latest stable version is bundled in jps
    for (LanguageVersion languageVersion : LanguageVersion.getEntries()) {
      if (languageVersion.compareTo(bundledLanguageVersion) <= 0) {
        latestStable = VersionView.Companion.deserialize(languageVersion.getVersionString(), false);
      }
      else {
        break;
      }
    }

    latestStableVersion = latestStable;
    return latestStable;
  }

  public void setTargetPlatform(@Nullable IdePlatformKind targetPlatform) {
    k2jsPanel.setVisible(JsIdePlatformUtil.isJavaScript(targetPlatform));
  }

  private void fillModuleKindList() {
    for (@Nls String moduleKind : moduleKindDescriptions.keySet()) {
      moduleKindComboBox.addItem(moduleKind);
    }

    moduleKindComboBox.setRenderer(BuilderKt.textListCellRenderer("", o -> getModuleKindDescription(o)));
  }

  private void fillSourceMapSourceEmbeddingList() {
    for (@Nls String moduleKind : sourceMapSourceEmbeddingDescriptions.keySet()) {
      sourceMapEmbedSources.addItem(moduleKind);
    }

    sourceMapEmbedSources.setRenderer(BuilderKt.textListCellRenderer("", o -> getSourceMapSourceEmbeddingDescription(o)));
  }

  @Override
  public @NotNull String getId() {
    return "project.kotlinCompiler";
  }

  @Override
  public @Nullable JComponent createComponent() {
    if (validatorsDisposable != null) {
      LOG.error(new IllegalStateException("validatorsDisposable is not null. Disposing and rewriting it."));
      Disposer.dispose(validatorsDisposable);
    }
    validatorsDisposable = Disposer.newDisposable();
    createVersionValidator(languageVersionComboBox, "configuration.warning.text.language.version.unsupported", validatorsDisposable);
    createVersionValidator(apiVersionComboBox, "configuration.warning.text.api.version.unsupported", validatorsDisposable);

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
           isCheckboxModified(copyRuntimeFilesCheckBox, compilerSettings.getCopyJsLibraryFiles()) ||
           isBrowseFieldModified(outputDirectory, compilerSettings.getOutputDirectoryForJsLibraryFiles()) ||

           (compilerWorkspaceSettings != null &&
            (isCheckboxModified(enableIncrementalCompilationForJvmCheckBox, compilerWorkspaceSettings.getPreciseIncrementalEnabled()) ||
             isCheckboxModified(enableIncrementalCompilationForJsCheckBox,
                                compilerWorkspaceSettings.getIncrementalCompilationForJsEnabled()) ||
             isCheckboxModified(keepAliveCheckBox, compilerWorkspaceSettings.getEnableDaemon()))) ||

           isCheckboxModified(generateSourceMapsCheckBox, k2jsCompilerArguments.getSourceMap()) ||
           !getSelectedModuleKind().equals(getModuleKindOrDefault(k2jsCompilerArguments.getModuleKind())) ||
           isFieldModified(sourceMapPrefix, StringUtil.notNullize(k2jsCompilerArguments.getSourceMapPrefix())) ||
           !getSelectedSourceMapSourceEmbedding().equals(
             getSourceMapSourceEmbeddingOrDefault(k2jsCompilerArguments.getSourceMapEmbedSources())) ||
           !getSelectedJvmVersion().equals(getJvmVersionOrDefault(k2jvmCompilerArguments.getJvmTarget()));
  }

  private @NotNull String getSelectedModuleKind() {
    return getModuleKindOrDefault((String)moduleKindComboBox.getSelectedItem());
  }

  private String getSelectedSourceMapSourceEmbedding() {
    return getSourceMapSourceEmbeddingOrDefault((String)sourceMapEmbedSources.getSelectedItem());
  }

  public @NotNull String getSelectedJvmVersion() {
    return getJvmVersionOrDefault((String)jvmVersionComboBox.getSelectedItem());
  }

  public @NotNull VersionView getSelectedLanguageVersionView() {
    Object item = languageVersionComboBox.getSelectedItem();
    return item != null ? (VersionView)item : getLatestStableVersion();
  }

  private @NotNull VersionView getSelectedAPIVersionView() {
    Object item = apiVersionComboBox.getSelectedItem();
    return item != null ? (VersionView)item : getLatestStableVersion();
  }

  public VersionView getSelectedKotlinJpsPluginVersionView() {
    JpsVersionItem selectedItem = (JpsVersionItem)kotlinJpsPluginVersionComboBox.getSelectedItem();
    IdeKotlinVersion version = selectedItem != null ? selectedItem.getVersion() : null;
    LanguageVersion languageVersion = version != null ? LanguageVersion.fromFullVersionString(version.toString()) : null;
    VersionView versionView;
    if (languageVersion != null) {
      versionView = new VersionView.Specific(languageVersion);
    }
    else {
      String compilerVersionFromSettings = KotlinJpsPluginSettings.getInstance(project).getSettings().getVersion();
      versionView = VersionView.Companion.deserialize(compilerVersionFromSettings, /*isAutoAdvance =*/ false);
    }
    return versionView;
  }

  private @NotNull String getSelectedKotlinJpsPluginVersion() {
    JpsVersionItem item = (JpsVersionItem)kotlinJpsPluginVersionComboBox.getSelectedItem();
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

      if (!project.isDefault() && shouldInvalidateCaches) {
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
    compilerSettings.setCopyJsLibraryFiles(copyRuntimeFilesCheckBox.isSelected());
    compilerSettings.setOutputDirectoryForJsLibraryFiles(outputDirectory.getText());

    if (compilerWorkspaceSettings != null) {
      compilerWorkspaceSettings.setPreciseIncrementalEnabled(enableIncrementalCompilationForJvmCheckBox.isSelected());
      compilerWorkspaceSettings.setIncrementalCompilationForJsEnabled(enableIncrementalCompilationForJsCheckBox.isSelected());
      compilerWorkspaceSettings.setDaemonVmOptions(extractDaemonVmOptions());

      boolean oldEnableDaemon = compilerWorkspaceSettings.getEnableDaemon();
      compilerWorkspaceSettings.setEnableDaemon(keepAliveCheckBox.isSelected());
      if (keepAliveCheckBox.isSelected() != oldEnableDaemon) {
        PluginStartupApplicationService.getInstance().resetAliveFlag();
      }
    }

    k2jsCompilerArguments.setSourceMap(generateSourceMapsCheckBox.isSelected());
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
          defaultJpsVersionItem = (JpsVersionItem)kotlinJpsPluginVersionComboBox.getSelectedItem();
          jpsPluginSettings.setVersion(jpsPluginVersion);
          KotlinJpsPluginSettings.getInstance(project).setSettings(jpsPluginSettings);
        }
      }

      KotlinCommonCompilerArgumentsHolder.getInstance(project).setSettings(commonCompilerArguments);
      Kotlin2JvmCompilerArgumentsHolder.getInstance(project).setSettings(k2jvmCompilerArguments);
      Kotlin2JsCompilerArgumentsHolder.getInstance(project).setSettings(k2jsCompilerArguments);
      KotlinCompilerSettings.getInstance(project).setSettings(compilerSettings);
    }

    if (!project.isDefault()) {
      BuildManager.getInstance().clearState(project);
    }
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
    // This call adds the correct values to the language/apiVersion dropdown based on the compiler version.
    // It also selects some values of the dropdown, but we want to choose the values reflecting the current settings afterward.
    onLanguageLevelChanged(getSelectedKotlinJpsPluginVersionView()); // getSelectedLanguageVersionView() replaces null

    if (!commonCompilerArguments.getAutoAdvanceLanguageVersion()) {
      setSelectedItem(languageVersionComboBox, KotlinFacetSettingsKt.getLanguageVersionView(commonCompilerArguments));
    }
    else {
      setSelectedItem(languageVersionComboBox, getLatestStableVersion());
    }
    if (!commonCompilerArguments.getAutoAdvanceApiVersion()) {
      setSelectedItem(apiVersionComboBox, KotlinFacetSettingsKt.getApiVersionView(commonCompilerArguments));
    }
    else {
      setSelectedItem(apiVersionComboBox, getLatestStableVersion());
    }
    additionalArgsOptionsField.setText(compilerSettings.getAdditionalArguments());
    copyRuntimeFilesCheckBox.setSelected(compilerSettings.getCopyJsLibraryFiles());
    outputDirectory.setText(compilerSettings.getOutputDirectoryForJsLibraryFiles());

    if (compilerWorkspaceSettings != null) {
      enableIncrementalCompilationForJvmCheckBox.setSelected(compilerWorkspaceSettings.getPreciseIncrementalEnabled());
      enableIncrementalCompilationForJsCheckBox.setSelected(compilerWorkspaceSettings.getIncrementalCompilationForJsEnabled());
      keepAliveCheckBox.setSelected(compilerWorkspaceSettings.getEnableDaemon());
    }

    generateSourceMapsCheckBox.setSelected(k2jsCompilerArguments.getSourceMap());

    moduleKindComboBox.setSelectedItem(getModuleKindOrDefault(k2jsCompilerArguments.getModuleKind()));
    sourceMapPrefix.setText(k2jsCompilerArguments.getSourceMapPrefix());
    sourceMapPrefix.setEnabled(k2jsCompilerArguments.getSourceMap());
    sourceMapEmbedSources.setSelectedItem(getSourceMapSourceEmbeddingOrDefault(k2jsCompilerArguments.getSourceMapEmbedSources()));

    jvmVersionComboBox.setSelectedItem(getJvmVersionOrDefault(k2jvmCompilerArguments.getJvmTarget()));
  }

  private static <T> void setSelectedItem(JComboBox<T> comboBox, T versionView) {
    // Imported projects might have outdated language/api versions - we display them as well (see createVersionValidator() for details)
    int index = ((MutableCollectionComboBoxModel<T>)comboBox.getModel()).getElementIndex(versionView);
    if (index == -1) {
      comboBox.addItem(versionView);
    }

    comboBox.setSelectedItem(versionView);
  }

  @Override
  public void disposeUIResources() {
    if (validatorsDisposable == null) {
      LOG.error(new IllegalStateException("validatorsDisposable is null"));
      return;
    }

    Disposer.dispose(validatorsDisposable);
    validatorsDisposable = null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return KotlinBaseCompilerConfigurationUiBundle.message("configuration.name.kotlin.compiler");
  }

  @Override
  public @Nullable String getHelpTopic() {
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

      if (!((JpsVersionItem)item).myEnabled) {
        return;
      }
      super.setSelectedItem(item);
    }
  }

  private static void createVersionValidator(JComboBox<VersionView> component, String messageKey, Disposable parentDisposable) {
    new ComponentValidator(parentDisposable)
      .withValidator(() -> {
        VersionView selectedItem = (VersionView)component.getSelectedItem();
        if (selectedItem == null) return null;

        LanguageOrApiVersion version = selectedItem.getVersion();
        if (version.isUnsupported()) {
          return new ValidationInfo(KotlinBaseCompilerConfigurationUiBundle.message(messageKey, version.getVersionString()), component);
        }

        return null;
      }).installOn(component);
    component.addActionListener(e -> ComponentValidator.getInstance(component).ifPresent(ComponentValidator::revalidate));
  }

  // Expected format is
  // -XdaemonVmOptions=-Xmx6000m for one argument
  // and
  // -XdaemonVmOptions=\"-Xmx6000m -XX:HeapDumpPath=kotlin-build-process-heap-dump.hprof -XX:+HeapDumpOnOutOfMemoryError\"
  // for multiple
  private @NotNull String extractDaemonVmOptions() {
    String additionalOptions = getAdditionalArgsOptionsField().getText();
    if (additionalOptions.contains("-XdaemonVmOptions")) {
      Pattern pattern = Pattern.compile("-XdaemonVmOptions=(?:\"([^\"]*)\"|([^\\s]*))");
      Matcher matcher = pattern.matcher(additionalOptions);

      if (matcher.find()) {
        String daemonArguments = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        if (daemonArguments != null) {
          return daemonArguments;
        }
      }
    }
    return "";
  }
}
