// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.highlighting.HighlightHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.find.FindBundle;
import com.intellij.find.FindInProjectSettings;
import com.intellij.find.FindSettings;
import com.intellij.icons.AllIcons;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.*;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceCommand;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.filters.FilterPanel;
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.TriConsumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.textCompletion.TextCompletionUtil;
import com.intellij.util.ui.GridBagConstraintBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TextTransferable;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;

/**
 * This dialog is used in two ways:
 * 1. a non-modal search dialog, showing a scope panel
 * 2. a modal edit dialog for Structural Search inspection patterns
 *
 * @author Bas Leijdekkers
 */
public class StructuralSearchDialog extends DialogWrapper implements DocumentListener {
  @NonNls private static final String SEARCH_DIMENSION_SERVICE_KEY = "#com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog";
  @NonNls private static final String REPLACE_DIMENSION_SERVICE_KEY = "#com.intellij.structuralsearch.plugin.ui.StructuralReplaceDialog";

  @NonNls private static final String SHORTEN_FQN_STATE = "structural.search.shorten.fqn";
  @NonNls private static final String REFORMAT_STATE = "structural.search.reformat";
  @NonNls private static final String USE_STATIC_IMPORT_STATE = "structural.search.use.static.import";
  @NonNls private static final String FILTERS_VISIBLE_STATE = "structural.search.filters.visible";
  @NonNls private static final String TEMPLATES_VISIBLE_STATE = "structural.search.filters.visible";
  @NonNls private static final String PINNED_STATE = "structural.seach.pinned";

  public static final Key<StructuralSearchDialog> STRUCTURAL_SEARCH_DIALOG = Key.create("STRUCTURAL_SEARCH_DIALOG");
  public static final Key<String> STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID = Key.create("STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID");
  public static final Key<Runnable> STRUCTURAL_SEARCH_ERROR_CALLBACK = Key.create("STRUCTURAL_SEARCH_ERROR_CALLBACK");
  private static final Key<Configuration> STRUCTURAL_SEARCH_PREVIOUS_CONFIGURATION = Key.create("STRUCTURAL_SEARCH_PREVIOUS_CONFIGURATION");
  public static final Key<Boolean> TEST_STRUCTURAL_SEARCH_DIALOG = Key.create("TEST_STRUCTURAL_SEARCH_DIALOG");

  @NotNull
  private final SearchContext mySearchContext;
  private Editor myEditor;
  private boolean myReplace;
  @NotNull
  private Configuration myConfiguration;
  @Nullable
  @NonNls private LanguageFileType myFileType = StructuralSearchUtil.getDefaultFileType();
  private Language myDialect;
  private PatternContext myPatternContext;
  private final List<RangeHighlighter> myRangeHighlighters = new SmartList<>();
  private final DocumentListener myRestartHighlightingListener = new DocumentListener() {
    final Runnable runnable = () -> ReadAction.nonBlocking(() -> addMatchHighlights())
      .withDocumentsCommitted(getProject())
      .expireWith(getDisposable())
      .coalesceBy(this)
      .submit(AppExecutorUtil.getAppExecutorService());

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      if (myAlarm.isDisposed()) return;
      myAlarm.cancelRequest(runnable);
      myAlarm.addRequest(runnable, 100);
    }
  };
  private boolean myChangedConfiguration;

  // ui management
  private final Alarm myAlarm;
  private boolean myUseLastConfiguration;
  private boolean myPinned;
  private final boolean myEditConfigOnly;

  // components
  private final FileTypeChooser myFileTypeChooser = new FileTypeChooser();
  private ActionToolbarImpl myOptionsToolbar;
  private EditorTextField mySearchCriteriaEdit;
  private EditorTextField myReplaceCriteriaEdit;
  private OnePixelSplitter mySearchEditorPanel;
  private OnePixelSplitter myMainSplitter;

  private FilterPanel myFilterPanel;
  private ExistingTemplatesComponent myExistingTemplatesComponent;
  private LinkComboBox myTargetComboBox;
  private ScopePanel myScopePanel;
  private JCheckBox myOpenInNewTab;

  private JComponent myReplacePanel;
  private SwitchAction mySwitchAction;
  private final ArrayList<JComponent> myComponentsWithEditorBackground = new ArrayList<>();
  private JComponent mySearchWrapper;
  private JBCheckBox myInjected;
  private JBCheckBox myMatchCase;
  private JComponent myReplaceWrapper;
  private JBCheckBox myShortenFqn;
  private JBCheckBox myStaticImport;
  private JBCheckBox myReformat;

  public StructuralSearchDialog(@NotNull SearchContext searchContext, boolean replace) {
    this(searchContext, replace, false);
  }

  public StructuralSearchDialog(@NotNull SearchContext searchContext, boolean replace, boolean editConfigOnly) {
    super(searchContext.getProject(), true);

    if (!editConfigOnly) {
      setModal(false);
      setOKButtonText(FindBundle.message("find.dialog.find.button"));
    }
    myReplace = replace;
    myEditConfigOnly = editConfigOnly;
    mySearchContext = searchContext;
    myEditor = searchContext.getEditor();
    addRestartHighlightingListenerToCurrentEditor();
    final FileEditorManagerListener listener = new FileEditorManagerListener() {
      FileEditor myNewEditor;
      final Runnable runnable = () -> {
        removeRestartHighlightingListenerFromCurrentEditor();
        removeMatchHighlights();
        if (myNewEditor instanceof TextEditor) {
          myEditor = ((TextEditor)myNewEditor).getEditor();
          addMatchHighlights();
          addRestartHighlightingListenerToCurrentEditor();
        }
        else {
          myEditor = null;
        }
      };

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        if (myAlarm.isDisposed()) return;
        myAlarm.cancelRequest(runnable);
        myNewEditor = event.getNewEditor();
        myAlarm.addRequest(runnable, 100);
      }
    };
    final MessageBusConnection connection = getProject().getMessageBus().connect(getDisposable());
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        close(CANCEL_EXIT_CODE);
      }
    });
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appClosing() {
        close(CANCEL_EXIT_CODE);
      }
    });
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        if (project == getProject()) {
          close(CANCEL_EXIT_CODE);
        }
      }
    });
    connection.subscribe(EditorColorsManager.TOPIC, uiSettings -> updateColors());
    myConfiguration = createConfiguration(null);
    setTitle(getDefaultTitle());

    init();
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
    setValidationDelay(100);
  }

  private void addRestartHighlightingListenerToCurrentEditor() {
    if (myEditor != null) {
      myEditor.getDocument().addDocumentListener(myRestartHighlightingListener);
    }
  }

  private void removeRestartHighlightingListenerFromCurrentEditor() {
    if (myEditor != null) {
      myEditor.getDocument().removeDocumentListener(myRestartHighlightingListener);
    }
  }

  public void setUseLastConfiguration(boolean useLastConfiguration) {
    myUseLastConfiguration = useLastConfiguration;
  }

  private EditorTextField createEditor(boolean replace) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    assert profile != null;
    final Document document = UIUtil.createDocument(getProject(), myFileType, myDialect, myPatternContext, "", profile);
    document.addDocumentListener(this, myDisposable);
    document.putUserData(STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID, (myPatternContext == null) ? "" : myPatternContext.getId());

    final EditorTextField textField = new MyEditorTextField(document, replace);
    textField.setFont(EditorFontType.getGlobalPlainFont());
    textField.setPreferredSize(new Dimension(550, 150));
    textField.setMinimumSize(new Dimension(200, 50));
    myComponentsWithEditorBackground.add(textField);
    return textField;
  }

  @Override
  public void documentChanged(@NotNull final DocumentEvent event) {
    initValidation();
    if (!myChangedConfiguration) {
      myExistingTemplatesComponent.templateChanged();
    }
  }

  private void initializeFilterPanel(@Nullable CompiledPattern compiledPattern) {
    final MatchOptions matchOptions = getConfiguration().getMatchOptions();
    final CompiledPattern finalCompiledPattern = compiledPattern == null
                                                 ? PatternCompiler.compilePattern(getProject(), matchOptions, false, false)
                                                 : compiledPattern;
    if (finalCompiledPattern == null) return;
    ApplicationManager.getApplication().invokeLater(() -> {
      SubstitutionShortInfoHandler.updateEditorInlays(mySearchCriteriaEdit.getEditor());
      if (myReplace) SubstitutionShortInfoHandler.updateEditorInlays(myReplaceCriteriaEdit.getEditor());
      myFilterPanel.setCompiledPattern(finalCompiledPattern);
      if (myFilterPanel.getVariable() == null) {
        myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(Configuration.CONTEXT_VAR_NAME, myConfiguration));
      }
    }, ModalityState.stateForComponent(myFilterPanel.getComponent()));
  }

  @NotNull
  private Configuration createConfiguration(Configuration template) {
    if (myReplace) {
      return (template == null) ? new ReplaceConfiguration(getUserDefined(), getUserDefined()) : new ReplaceConfiguration(template);
    }
    if (template == null) {
      return new SearchConfiguration(getUserDefined(), getUserDefined());
    }
    return (template instanceof ReplaceConfiguration) ? new ReplaceConfiguration(template) : new SearchConfiguration(template);
  }

  static @Nls(capitalization = Nls.Capitalization.Sentence) String getUserDefined() {
    return SSRBundle.message("new.template.defaultname");
  }

  private void setTextFromContext() {
    final Editor editor = myEditor;
    if (editor != null) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      final String selectedText = selectionModel.getSelectedText();
      if (selectedText != null) {
        if (loadConfiguration(selectedText)) {
          return;
        }
        final String text = selectedText.trim();
        setTextForEditor(text.trim(), mySearchCriteriaEdit);
        if (myReplace) {
          setTextForEditor(text, myReplaceCriteriaEdit);
        }
        myScopePanel.setScopesFromContext(null);
        ApplicationManager.getApplication().invokeLater(() -> startTemplate());
        return;
      }
    }

    final Configuration previousConfiguration = getProject().getUserData(STRUCTURAL_SEARCH_PREVIOUS_CONFIGURATION);
    if (previousConfiguration != null) {
      loadConfiguration(previousConfiguration);
    }
    else {
      final Configuration configuration = ConfigurationManager.getInstance(getProject()).getMostRecentConfiguration();
      if (configuration != null) {
        loadConfiguration(configuration);
      }
    }
  }

  private void setTextForEditor(String text, EditorTextField editor) {
    editor.setText(text);
    editor.selectAll();
    final Project project = getProject();
    final Document document = editor.getDocument();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    final PsiFile file = documentManager.getPsiFile(document);
    if (file == null) return;

    WriteCommandAction.runWriteCommandAction(project, SSRBundle.message("command.name.adjust.line.indent"), "Structural Search",
                                             () -> CodeStyleManager.getInstance(project)
                                               .adjustLineIndent(file, new TextRange(0, document.getTextLength())), file);
  }

  private void startSearching() {
    if (myReplace) {
      new ReplaceCommand(myConfiguration, mySearchContext).startSearching();
    }
    else {
      new SearchCommand(myConfiguration, mySearchContext).startSearching();
    }
  }

  @NotNull
  @Nls
  @NlsContexts.DialogTitle
  private String getDefaultTitle() {
    return myReplace ? SSRBundle.message("structural.replace.title") : SSRBundle.message("structural.search.title");
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected JComponent createCenterPanel() {
    final var searchPanel = createSearchPanel();

    myScopePanel = new ScopePanel(getProject(), myDisposable);
    if (!myEditConfigOnly) {
      myScopePanel.setRecentDirectories(FindInProjectSettings.getInstance(getProject()).getRecentDirectories());
      myScopePanel.setScopeConsumer(scope -> initValidation());
    }
    else {
      myScopePanel.setVisible(false);
    }

    myReplacePanel = createReplacePanel();
    myReplacePanel.setVisible(myReplace);

    final GridBagLayout centerPanelLayout = new GridBagLayout();
    final var centerConstraint = new GridBagConstraintBuilder();
    final JPanel centerPanel = new JPanel(centerPanelLayout);
    centerPanel.add(searchPanel, centerConstraint.newLine().fillXY().growXY().get());
    centerPanel.add(myReplacePanel, centerConstraint.newLine().fillXY().growXY().get());
    centerPanel.add(myScopePanel, centerConstraint.newLine().fillX().growX().insets(8, DEFAULT_HGAP, 8, DEFAULT_HGAP).get());

    myExistingTemplatesComponent = new ExistingTemplatesComponent(getProject());
    myExistingTemplatesComponent.onConfigurationSelected(configuration -> {
      loadConfiguration(configuration);
    });
    myExistingTemplatesComponent.setConfigurationProducer(() -> myConfiguration);
    myExistingTemplatesComponent.setSearchEditorProducer(() -> mySearchCriteriaEdit);
    myExistingTemplatesComponent.setExportRunnable(() -> exportToClipboard());
    myExistingTemplatesComponent.setImportRunnable(() -> importFromClipboard());
    myMainSplitter = new OnePixelSplitter(false, 0.2f);
    myMainSplitter.setFirstComponent(myExistingTemplatesComponent.getTemplatesPanel());
    myMainSplitter.setSecondComponent(centerPanel);

    updateColors();
    updateOptions();
    return myMainSplitter;
  }

  private JPanel createSearchPanel() {
    // 'Search template:' label
    final JBLabel searchTemplateLabel = new JBLabel(SSRBundle.message("search.template"));

    // File type chooser
    myFileType = UIUtil.detectFileType(mySearchContext);
    myDialect = myFileType.getLanguage();
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    if (profile != null) {
      final List<PatternContext> contexts = profile.getPatternContexts();
      if (!contexts.isEmpty()) {
        myPatternContext = contexts.get(0);
      }
    }
    myFileTypeChooser.setSelectedItem(myFileType, myDialect, myPatternContext);
    myFileTypeChooser.setFileTypeInfoConsumer(info -> {
      if (info == null) {
        myFileType = null;
        myDialect = null;
        myPatternContext = null;
      }
      else {
        myFileType = info.getFileType();
        myDialect = info.getDialect();
        myPatternContext = info.getContext();
      }
      myOptionsToolbar.updateActionsImmediately();
      myFilterPanel.setFileType(myFileType);
      final String contextId = (myPatternContext == null) ? "" : myPatternContext.getId();
      final StructuralSearchProfile profile1 = StructuralSearchUtil.getProfileByFileType(myFileType);

      final Document searchDocument =
        UIUtil.createDocument(getProject(), myFileType, myDialect, myPatternContext, mySearchCriteriaEdit.getText(), profile1);
      searchDocument.addDocumentListener(this, myDisposable);
      mySearchCriteriaEdit.setNewDocumentAndFileType((myFileType == null) ? PlainTextFileType.INSTANCE : myFileType, searchDocument);
      searchDocument.putUserData(STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID, contextId);

      final Document replaceDocument =
        UIUtil.createDocument(getProject(), myFileType, myDialect, myPatternContext, myReplaceCriteriaEdit.getText(), profile1);
      replaceDocument.addDocumentListener(this, myDisposable);
      myReplaceCriteriaEdit.setNewDocumentAndFileType((myFileType == null) ? PlainTextFileType.INSTANCE : myFileType, replaceDocument);
      replaceDocument.putUserData(STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID, contextId);

      initValidation();
      updateOptions();
    });
    myFileTypeChooser.setUserActionFileTypeInfoConsumer(info -> {
      myExistingTemplatesComponent.selectFileType(info.getText());
    });

    // Existing templates action
    final AnAction showTemplatesAction = new DumbAwareToggleAction(SSRBundle.message("templates.button"),
                                                                   SSRBundle.message("templates.button.description"),
                                                                   AllIcons.Actions.PreviewDetails) {
      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return isExistingTemplatesPanelVisible();
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        setExistingTemplatesPanelVisible(state);
      }
    };

    // Filter action
    final Icon filterModifiedIcon = ExecutionUtil.getLiveIndicator(AllIcons.General.Filter);
    final AnAction filterAction = new DumbAwareToggleAction(SSRBundle.message("filter.button"),
                                                            SSRBundle.message("filter.button.description"),
                                                            filterModifiedIcon) {

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        final Presentation presentation = e.getPresentation();
        presentation.setIcon(myFilterPanel.hasVisibleFilter() ? filterModifiedIcon : AllIcons.General.Filter);
      }

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return isFilterPanelVisible();
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        setFilterPanelVisible(state);
      }
    };

    // Filter panel
    myFilterPanel = new FilterPanel(getProject(), myFileType, getDisposable());
    myFilterPanel.setConstraintChangedCallback(() -> initValidation());
    myFilterPanel.getComponent().setMinimumSize(new Dimension(300, 50));

    // Pin action
    final DumbAwareToggleAction pinAction =
      new DumbAwareToggleAction(SSRBundle.message("pin.button"), SSRBundle.message("pin.button.description"), AllIcons.General.Pin_tab) {

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
          return myPinned;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
          myPinned = state;
        }
      };

    // Switch action
    mySwitchAction = new SwitchAction();

    final DefaultActionGroup optionsActionGroup =
      new DefaultActionGroup(myFileTypeChooser, showTemplatesAction, filterAction, new Separator(), pinAction, mySwitchAction);
    final ActionManager actionManager = ActionManager.getInstance();
    myOptionsToolbar = (ActionToolbarImpl)actionManager.createActionToolbar("StructuralSearchDialog", optionsActionGroup, true);
    myOptionsToolbar.setTargetComponent(mySearchCriteriaEdit);
    myOptionsToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myOptionsToolbar.setForceMinimumSize(true);

    // Search editor panel, 1st splitter element
    final var searchEditorPanel = new JPanel(new GridBagLayout());
    final var searchConstraint = new GridBagConstraintBuilder();

    // Search panel options
    myTargetComboBox = new LinkComboBox(SSRBundle.message("complete.match.variable.name"));
    myTargetComboBox.setItemConsumer(item -> {
      final MatchOptions matchOptions = myConfiguration.getMatchOptions();
      for (String name : matchOptions.getVariableConstraintNames()) {
        matchOptions.getVariableConstraint(name).setPartOfSearchResults(name.equals(item));
      }
      initValidation();
    });
    final String text = SSRBundle.message("search.target.label");
    final JLabel searchTargetLabel = new JLabel(text);
    myTargetComboBox.setLabel(searchTargetLabel);

    myInjected = new JBCheckBox(SSRBundle.message("search.in.injected.checkbox"));
    myInjected.setOpaque(false);
    myInjected.addActionListener(e -> {
      myConfiguration.getMatchOptions().setSearchInjectedCode(myInjected.isSelected());
      initValidation();
    });

    myMatchCase = new JBCheckBox(FindBundle.message("find.popup.case.sensitive"));
    myMatchCase.setOpaque(false);
    myMatchCase.addActionListener(e -> {
      myConfiguration.getMatchOptions().setCaseSensitiveMatch(myMatchCase.isSelected());
      initValidation();
    });

    // Splitter
    mySearchEditorPanel = new OnePixelSplitter(false, 1.0f);
    mySearchEditorPanel.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE);
    mySearchCriteriaEdit = createEditor(false);
    mySearchEditorPanel.setFirstComponent(searchEditorPanel);
    myComponentsWithEditorBackground.add(searchEditorPanel);

    // Wrapper
    mySearchWrapper = new JPanel(new BorderLayout()); // needed for border
    mySearchWrapper.add(mySearchEditorPanel, BorderLayout.CENTER);

    searchEditorPanel.add(mySearchCriteriaEdit, searchConstraint.width(5).fillXY().growXY().get());
    searchEditorPanel.add(searchTargetLabel, searchConstraint.newLine().noFill().noGrow().width(1).insets(10, DEFAULT_HGAP, 10, 0).get());
    searchEditorPanel.add(myTargetComboBox, searchConstraint.get());
    searchEditorPanel.add(myInjected, searchConstraint.get());
    searchEditorPanel.add(myMatchCase, searchConstraint.insets(10, DEFAULT_HGAP, 10, DEFAULT_HGAP).get());

    mySearchEditorPanel.setSecondComponent(myFilterPanel.getComponent());
    myComponentsWithEditorBackground.add(myFilterPanel.getTable());

    final JPanel searchPanel = new JPanel(new GridBagLayout());
    final var northConstraint = new GridBagConstraintBuilder();
    searchPanel.add(searchTemplateLabel, northConstraint.insets(6, DEFAULT_HGAP, 6, 0).get());
    searchPanel.add(myOptionsToolbar.getComponent(), northConstraint.growX().anchorEnd().insets(6, 0, 6, DEFAULT_HGAP).get());
    searchPanel.add(mySearchWrapper, northConstraint.newLine().fillXY().growXY().width(2).insets(0, 0, 0, 0).get());
    return searchPanel;
  }

  private @NotNull JComponent createReplacePanel() {
    final JBLabel replacementTemplateLabel = new JBLabel(SSRBundle.message("replacement.template.label"));

    myShortenFqn = new JBCheckBox(SSRBundle.message("shorten.fully.qualified.names.checkbox"));
    myShortenFqn.setOpaque(false);
    myShortenFqn.addActionListener(e -> {
      myConfiguration.getReplaceOptions().setToShortenFQN(myShortenFqn.isSelected());
    });

    myStaticImport = new JBCheckBox(SSRBundle.message("use.static.import.checkbox"));
    myStaticImport.setOpaque(false);
    myStaticImport.addActionListener(e -> {
      myConfiguration.getReplaceOptions().setToUseStaticImport(myStaticImport.isSelected());
    });

    myReformat = new JBCheckBox(SSRBundle.message("reformat.checkbox"));
    myReformat.setOpaque(false);
    myReformat.addActionListener(e -> {
      myConfiguration.getReplaceOptions().setToReformatAccordingToStyle(myReformat.isSelected());
    });

    myReplaceCriteriaEdit = createEditor(true);
    myReplaceWrapper = new JPanel(new GridBagLayout());
    final var wrapperConstraint = new GridBagConstraintBuilder();
    myComponentsWithEditorBackground.add(myReplaceWrapper);

    myReplaceWrapper.add(myReplaceCriteriaEdit, wrapperConstraint.width(4).fillXY().growXY().get());
    myReplaceWrapper.add(myShortenFqn, wrapperConstraint.newLine().width(1).noFill().noGrow().insets(10, 10, 10, 0).get());
    myReplaceWrapper.add(myStaticImport, wrapperConstraint.get());
    myReplaceWrapper.add(myReformat, wrapperConstraint.get());

    final JPanel replacePanel = new JPanel(new GridBagLayout());
    final var replaceConstraint = new GridBagConstraintBuilder();
    replacePanel.add(replacementTemplateLabel, replaceConstraint.fillX().growX().insets(16, DEFAULT_HGAP, 14, 0).get());
    replacePanel.add(myReplaceWrapper, replaceConstraint.newLine().fillXY().growXY().noInsets().get());
    return replacePanel;
  }

  private void updateOptions() {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    final TriConsumer<JBCheckBox, Boolean, Boolean> initReplaceCheckbox = (checkBox, enabledAndVisible, selected) -> {
      checkBox.setEnabled(enabledAndVisible); checkBox.setVisible(enabledAndVisible);
      checkBox.setSelected(selected);
    };

    // Search options
    if (myInjected == null) return;
    myInjected.setSelected(myConfiguration.getMatchOptions().isSearchInjectedCode());
    myInjected.setVisible(!myEditConfigOnly);
    myMatchCase.setSelected(myConfiguration.getMatchOptions().isCaseSensitiveMatch());

    // Replace options
    if (myShortenFqn == null) return;
    initReplaceCheckbox.accept(myShortenFqn,
                               profile != null && profile.supportsShortenFQNames(),
                               myReplace && myConfiguration.getReplaceOptions().isToShortenFQN());

    initReplaceCheckbox.accept(myStaticImport,
                               profile != null && profile.supportsUseStaticImports(),
                               myReplace && myConfiguration.getReplaceOptions().isToUseStaticImport());
    myReformat.setSelected(myReplace && myConfiguration.getReplaceOptions().isToReformatAccordingToStyle());
  }

  @Nullable
  @Override
  protected JPanel createSouthAdditionalPanel() {
    if (myEditConfigOnly) return null;
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
    myOpenInNewTab = new JCheckBox(SSRBundle.message("open.in.new.tab.checkbox"));
    myOpenInNewTab.setSelected(FindSettings.getInstance().isShowResultsInSeparateView());
    panel.add(myOpenInNewTab, BorderLayout.EAST);
    return panel;
  }

  private Project getProject() {
    return mySearchContext.getProject();
  }

  @Nullable
  @Override
  public Point getInitialLocation() {
    // handle dimension service manually to store dimensions correctly when switching between search/replace in the same dialog
    final DimensionService dimensionService = DimensionService.getInstance();
    final Dimension size = dimensionService.getSize(myReplace ? REPLACE_DIMENSION_SERVICE_KEY : SEARCH_DIMENSION_SERVICE_KEY, getProject());
    if (size != null) {
      setSize(size.width, myEditConfigOnly ? size.height - myScopePanel.getPreferredSize().height : size.height);
    }
    else {
      pack();
      // set width from replace if search not available and vice versa
      final Dimension otherSize =
        dimensionService.getSize(myReplace ? SEARCH_DIMENSION_SERVICE_KEY : REPLACE_DIMENSION_SERVICE_KEY, getProject());
      if (otherSize != null) {
        setSize(otherSize.width, getSize().height);
      }
    }
    if (myEditConfigOnly) return super.getInitialLocation();
    final Point location = dimensionService.getLocation(SEARCH_DIMENSION_SERVICE_KEY, getProject());
    return (location == null) ? super.getInitialLocation() : location;
  }

  @Override
  public void show() {
    if (!myUseLastConfiguration) {
      setTextFromContext();
    }
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    setFilterPanelVisible(properties.getBoolean(FILTERS_VISIBLE_STATE, true));
    setExistingTemplatesPanelVisible(properties.getBoolean(TEMPLATES_VISIBLE_STATE, true));
    myPinned = properties.getBoolean(PINNED_STATE, false);
    super.show();
  }

  private void startTemplate() {
    if (!Registry.is("ssr.template.from.selection.builder")) {
      return;
    }
    final Document document = mySearchCriteriaEdit.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
    assert psiFile != null;
    final TemplateBuilder builder = StructuralSearchTemplateBuilder.getInstance().buildTemplate(psiFile);
    if (builder == null) return;
    WriteCommandAction
      .runWriteCommandAction(getProject(), SSRBundle.message("command.name.live.search.template.builder"), "Structural Search",
                             () -> builder.run(Objects.requireNonNull(mySearchCriteriaEdit.getEditor()), true));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySearchCriteriaEdit;
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
    removeMatchHighlights();
  }

  @Override
  protected void doOKAction() {
    if (!getOKAction().isEnabled()) return;
    if (!myPinned) close(OK_EXIT_CODE);
    removeMatchHighlights();
    myAlarm.cancelAllRequests();
    myConfiguration.removeUnusedVariables();
    if (myEditConfigOnly) return;

    final SearchScope scope = myScopePanel.getScope();
    if (scope instanceof GlobalSearchScopesCore.DirectoryScope) {
      final GlobalSearchScopesCore.DirectoryScope directoryScope = (GlobalSearchScopesCore.DirectoryScope)scope;
      FindInProjectSettings.getInstance(getProject()).addDirectory(directoryScope.getDirectory().getPresentableUrl());
    }

    FindSettings.getInstance().setShowResultsInSeparateView(myOpenInNewTab.isSelected());
    ConfigurationManager.getInstance(getProject()).addHistoryConfiguration(myConfiguration);
    startSearching();
  }

  public Configuration getConfiguration() {
    saveConfiguration();
    return myReplace ? new ReplaceConfiguration(myConfiguration) : new SearchConfiguration(myConfiguration);
  }

  private void removeMatchHighlights() {
    if (myEditConfigOnly || myRangeHighlighters.isEmpty()) {
      return;
    }
    // retrieval of editor needs to be outside invokeLater(), otherwise the editor might have already changed.
    final Editor editor = myEditor;
    if (editor == null) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      final Project project = getProject();
      if (project.isDisposed()) {
        return;
      }
      final HighlightManager highlightManager = HighlightManager.getInstance(project);
      for (RangeHighlighter highlighter : myRangeHighlighters) {
        highlightManager.removeSegmentHighlighter(editor, highlighter);
      }
      WindowManager.getInstance().getStatusBar(project).setInfo("");
      myRangeHighlighters.clear();
    });
  }

  private void addMatchHighlights() {
    if (myEditConfigOnly || DumbService.isDumb(getProject())) {
      // Search hits in the current editor are not shown when dumb.
      return;
    }
    final Project project = getProject();
    final Editor editor = myEditor;
    if (editor == null) {
      return;
    }
    final Document document = editor.getDocument();
    final PsiFile file = ReadAction.nonBlocking(() -> PsiDocumentManager.getInstance(project).getPsiFile(document)).executeSynchronously();
    if (file == null) {
      return;
    }
    final MatchOptions matchOptions = getConfiguration().getMatchOptions();
    matchOptions.setScope(new LocalSearchScope(file, PredefinedSearchScopeProviderImpl.getCurrentFileScopeName()));
    final CollectingMatchResultSink sink = new CollectingMatchResultSink();
    try {
      new Matcher(project, matchOptions).findMatches(sink);
      final List<MatchResult> matches = sink.getMatches();
      removeMatchHighlights();
      addMatchHighlights(matches, editor, file, SSRBundle.message("status.bar.text.results.found.in.current.file", matches.size()));
    }
    catch (StructuralSearchException e) {
      reportMessage(e.getMessage().replace(ScriptSupport.UUID, ""), true, mySearchCriteriaEdit);
      removeMatchHighlights();
    }
  }

  private void addMatchHighlights(@NotNull List<? extends MatchResult> matchResults,
                                  @NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NlsContexts.StatusBarText @Nullable String statusBarText) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final Project project = getProject();
      if (project.isDisposed()) {
        return;
      }
      if (!matchResults.isEmpty()) {
        for (MatchResult result : matchResults) {
          final PsiElement match = result.getMatch();
          if (match == null || match.getContainingFile() != file) continue;
          int start = -1;
          int end = -1;
          if (MatchResult.MULTI_LINE_MATCH.equals(result.getName())) {
            for (MatchResult child : result.getChildren()) {
              final TextRange range = child.getMatch().getTextRange();
              final int startOffset = range.getStartOffset();
              if (start == -1 || start > startOffset) {
                start = startOffset;
              }
              final int endOffset = range.getEndOffset();
              if (end < endOffset) {
                end = endOffset;
              }
            }
          }
          else {
            final TextRange range = match.getTextRange();
            start = range.getStartOffset();
            end = range.getEndOffset();
          }
          final HighlightManager highlightManager = HighlightManager.getInstance(project);
          highlightManager.addRangeHighlight(editor, start, end, EditorColors.SEARCH_RESULT_ATTRIBUTES, false, myRangeHighlighters);
        }
        HighlightHandlerBase.setupFindModel(project);
      }
      WindowManager.getInstance().getStatusBar(project).setInfo(statusBarText);
    });
  }

  @Override
  protected boolean continuousValidation() {
    return false;
  }

  @Override
  protected @NotNull Alarm.ThreadToUse getValidationThreadToUse() {
    return Alarm.ThreadToUse.POOLED_THREAD;
  }

  @Override
  protected @NotNull List<ValidationInfo> doValidateAll() {
    final JRootPane component = getRootPane();
    if (component == null) {
      return Collections.emptyList();
    }
    final List<ValidationInfo> errors = new SmartList<>();
    final MatchOptions matchOptions = getConfiguration().getMatchOptions();
    try {
      final Project project = getProject();
      CompiledPattern compiledPattern = null;
      try {
        compiledPattern = PatternCompiler.compilePattern(project, matchOptions, true, !myEditConfigOnly && !myPerformAction);
      }
      catch (MalformedPatternException e) {
        removeMatchHighlights();
        if (!StringUtil.isEmptyOrSpaces(matchOptions.getSearchPattern())) {
          final String message = e.getMessage();
          errors.add(new ValidationInfo((message == null)
                                        ? SSRBundle.message("this.pattern.is.malformed.message")
                                        : message,
                                        mySearchCriteriaEdit));
        }
      }
      catch (UnsupportedPatternException | NoMatchFoundException e) {
        removeMatchHighlights();
        errors.add(new ValidationInfo(e.getMessage(), mySearchCriteriaEdit));
      }
      if (myReplace) {
        try {
          Replacer.checkReplacementPattern(getProject(), myConfiguration.getReplaceOptions());
        }
        catch (UnsupportedPatternException | MalformedPatternException e) {
          errors.add(new ValidationInfo(e.getMessage(), myReplaceCriteriaEdit));
        }
      }

      initializeFilterPanel(compiledPattern);
      if (compiledPattern != null) {
        addMatchHighlights();
      }
      else {
        errors.add(new ValidationInfo(""));
      }
      ApplicationManager.getApplication().invokeLater(() -> {
        setSearchTargets(myConfiguration.getMatchOptions());
      }, ModalityState.stateForComponent(component));
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (RuntimeException e) {
      Logger.getInstance(StructuralSearchDialog.class).error(e);
    }
    return errors;
  }

  private Balloon myBalloon;
  private void reportMessage(@NlsContexts.PopupContent @Nullable String message, boolean error, @NotNull JComponent component) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (isDisposed()) return;
      if (myBalloon != null) myBalloon.hide();

      if (message == null) return;
      myBalloon = JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(message, error ? MessageType.ERROR : MessageType.WARNING, null)
        .setHideOnFrameResize(false)
        .createBalloon();
      myBalloon.show(new RelativePoint(component, new Point(component.getWidth() / 2, component.getHeight())), Balloon.Position.below);
      Disposer.register(myDisposable, myBalloon);
    }, ModalityState.stateForComponent(component));
  }

  private void securityCheck() {
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    int scripts = 0;
    for (String name : matchOptions.getVariableConstraintNames()) {
      final MatchVariableConstraint constraint = matchOptions.getVariableConstraint(name);
      if (constraint.getScriptCodeConstraint().length() > 2) scripts++;
    }
    if (myConfiguration instanceof ReplaceConfiguration) {
      final ReplaceOptions replaceOptions = myConfiguration.getReplaceOptions();
      for (ReplacementVariableDefinition variableDefinition : replaceOptions.getVariableDefinitions()) {
        if (variableDefinition.getScriptCodeConstraint().length() > 2) scripts++;
      }
    }
    if (scripts > 0) {
      NotificationGroupManager.getInstance()
        .getNotificationGroup(UIUtil.SSR_NOTIFICATION_GROUP_ID)
        .createNotification(
          SSRBundle.message("import.template.script.warning.title"),
          SSRBundle.message("import.template.script.warning", ApplicationNamesInfo.getInstance().getFullProductName(), scripts),
          NotificationType.WARNING
        )
        .notify(mySearchContext.getProject());
    }
  }

  public void showFilterPanel(String variableName) {
    myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(variableName, myConfiguration));
    setFilterPanelVisible(true);
    myConfiguration.setCurrentVariableName(variableName);
  }

  private void setFilterPanelVisible(boolean visible) {
    if (visible) {
      if (myFilterPanel.getVariable() == null) {
        myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(Configuration.CONTEXT_VAR_NAME, myConfiguration));
      }
      if (!isFilterPanelVisible()) {
        mySearchEditorPanel.setSecondComponent(myFilterPanel.getComponent());
      }
    }
    else {
      if (isFilterPanelVisible()) {
        mySearchEditorPanel.setSecondComponent(null);
        myConfiguration.setCurrentVariableName(null);
      }
    }
  }

  private boolean isFilterPanelVisible() {
    return mySearchEditorPanel.getSecondComponent() != null;
  }

  private void setExistingTemplatesPanelVisible(boolean visible) {
    if (visible) {
      myMainSplitter.setFirstComponent(myExistingTemplatesComponent.getTemplatesPanel());
    } else {
        myMainSplitter.setFirstComponent(null);
    }
  }

  private boolean isExistingTemplatesPanelVisible() {
    return myMainSplitter.getFirstComponent() != null;
  }

  private void setSearchTargets(MatchOptions matchOptions) {
    final List<String> names = new ArrayList<>(matchOptions.getUsedVariableNames());
    Collections.sort(names);
    names.remove(Configuration.CONTEXT_VAR_NAME);
    names.add(SSRBundle.message("complete.match.variable.name"));
    myTargetComboBox.setItems(names);
    myTargetComboBox.setEnabled(names.size() > 1);

    for (@NlsSafe String name : names) {
      final MatchVariableConstraint constraint = matchOptions.getVariableConstraint(name);
      if (constraint != null && constraint.isPartOfSearchResults()) {
        myTargetComboBox.setSelectedItem(name);
        return;
      }
    }
    myTargetComboBox.setSelectedItem(SSRBundle.message("complete.match.variable.name"));
  }

  /**
   * @param text  the text to try and load a configuration from
   * @return {@code true}, if some configuration was found, even if it was broken or corrupted {@code false} otherwise.
   */
  private boolean loadConfiguration(String text) {
    if (text == null) {
      return false;
    }
    try {
      final Configuration configuration = ConfigurationUtil.fromXml(text);
      if (configuration == null) {
        return false;
      }
      if (configuration instanceof ReplaceConfiguration && !myReplace) {
        mySwitchAction.actionPerformed(
          AnActionEvent.createFromAnAction(mySwitchAction, null, ActionPlaces.UNKNOWN, DataContext.EMPTY_CONTEXT));
      }
      loadConfiguration(configuration);
      securityCheck();
    }
    catch (JDOMException e) {
      reportMessage(SSRBundle.message("import.template.script.corrupted") + '\n' + e.getMessage(), false, myOptionsToolbar);
    }
    return true;
  }

  public void loadConfiguration(Configuration configuration) {
    final Configuration newConfiguration = createConfiguration(configuration);
    if (myUseLastConfiguration) {
      newConfiguration.setUuid(myConfiguration.getUuid());
      newConfiguration.setName(myConfiguration.getName());
      newConfiguration.setDescription(myConfiguration.getDescription());
      newConfiguration.setSuppressId(myConfiguration.getSuppressId());
      newConfiguration.setProblemDescriptor(myConfiguration.getProblemDescriptor());
    }
    myConfiguration = newConfiguration;
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    setSearchTargets(matchOptions);
    if (!myEditConfigOnly) {
      final SearchScope scope = matchOptions.getScope();
      myScopePanel.setScopesFromContext(scope);
      if (myUseLastConfiguration) {
        myScopePanel.setScope(scope);
      }
    }

    myFileTypeChooser.setSelectedItem(matchOptions.getFileType(), matchOptions.getDialect(), matchOptions.getPatternContext());
    final Editor searchEditor = mySearchCriteriaEdit.getEditor();
    if (searchEditor != null) {
      searchEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
    }
    setEditorContent(false, matchOptions.getSearchPattern());

    if (myReplace) {
      final Editor replaceEditor = myReplaceCriteriaEdit.getEditor();
      if (replaceEditor != null) {
        replaceEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
      }
      if (configuration instanceof ReplaceConfiguration) {
        final ReplaceOptions replaceOptions = configuration.getReplaceOptions();
        setEditorContent(true, replaceOptions.getReplacement());
      }
      else {
        setEditorContent(true, matchOptions.getSearchPattern());
      }
    }
    updateOptions();
  }

  private void setEditorContent(boolean replace, String text) {
    myChangedConfiguration = true;
    UIUtil.setContent(replace ? myReplaceCriteriaEdit : mySearchCriteriaEdit, text);
    myChangedConfiguration = false;
  }

  private void saveConfiguration() {
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();

    if (!myEditConfigOnly) {
      final SearchScope scope = myScopePanel.getScope();
      final boolean searchWithinHierarchy = IdeBundle.message("scope.class.hierarchy").equals(scope.getDisplayName());
      // We need to reset search within hierarchy scope during online validation since the scope works with user participation
      matchOptions.setScope(searchWithinHierarchy && !myPerformAction ? GlobalSearchScope.projectScope(getProject()) : scope);
    }
    else {
      matchOptions.setScope(null);
    }
    if (myFileType != null) {
      matchOptions.setFileType(myFileType);
    }
    matchOptions.setDialect(myDialect);
    matchOptions.setPatternContext(myPatternContext);
    matchOptions.setSearchPattern(getPattern(mySearchCriteriaEdit));
    matchOptions.setRecursiveSearch(!myReplace);

    final PropertiesComponent properties = PropertiesComponent.getInstance();
    if (myReplace) {
      final ReplaceOptions replaceOptions = myConfiguration.getReplaceOptions();
      replaceOptions.setReplacement(getPattern(myReplaceCriteriaEdit));
      properties.setValue(SHORTEN_FQN_STATE, replaceOptions.isToShortenFQN());
      properties.setValue(USE_STATIC_IMPORT_STATE, replaceOptions.isToUseStaticImport());
      properties.setValue(REFORMAT_STATE, replaceOptions.isToReformatAccordingToStyle());
    }
  }

  private String getPattern(EditorTextField textField) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    if (profile != null) {
      final Document document = textField.getDocument();
      final String pattern = ReadAction.nonBlocking(() -> {
        final PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
        assert file != null;
        return profile.getCodeFragmentText(file);
      }).executeSynchronously();
      return pattern.isEmpty() ? textField.getText() : pattern;
    }
    return textField.getText();
  }

  @Nullable
  @Override
  protected final String getDimensionServiceKey() {
    return null;
  }

  @Override
  public void dispose() {
    getProject().putUserData(STRUCTURAL_SEARCH_PREVIOUS_CONFIGURATION, myConfiguration);
    storeDimensions();

    if (mySearchEditorPanel != null) {
      final PropertiesComponent properties = PropertiesComponent.getInstance();
      properties.setValue(FILTERS_VISIBLE_STATE, isFilterPanelVisible(), true);
      properties.setValue(TEMPLATES_VISIBLE_STATE, isExistingTemplatesPanelVisible(), true);
      properties.setValue(PINNED_STATE, myPinned);
    }
    StructuralSearchPlugin.getInstance(getProject()).setDialog(null);
    myAlarm.cancelAllRequests();
    mySearchCriteriaEdit.removeNotify();
    myReplaceCriteriaEdit.removeNotify();
    removeRestartHighlightingListenerFromCurrentEditor();
    super.dispose();
  }

  /**
   * Handle own dimension service to store dimensions correctly when switching between search/replace in the same dialog
   */
  private void storeDimensions() {
    if (myEditConfigOnly) return; // don't store dimensions when editing structural search inspection patterns

    final String key1 = myReplace ? REPLACE_DIMENSION_SERVICE_KEY : SEARCH_DIMENSION_SERVICE_KEY;
    final String key2 = myReplace ? SEARCH_DIMENSION_SERVICE_KEY : REPLACE_DIMENSION_SERVICE_KEY;
    final Point location = getLocation();
    if (location.x < 0) location.x = 0;
    if (location.y < 0) location.y = 0;
    final DimensionService dimensionService = DimensionService.getInstance();
    dimensionService.setLocation(SEARCH_DIMENSION_SERVICE_KEY, location, getProject());
    final Dimension size = getSize();
    dimensionService.setSize(key1, size, getProject());
    final Dimension otherSize = dimensionService.getSize(key2, getProject());
    if (otherSize != null && otherSize.width != size.width) {
      otherSize.width = size.width;
      dimensionService.setSize(key2, otherSize, getProject());
    }
  }

  @Override
  protected String getHelpId() {
    return "find.structuredSearch";
  }

  private void updateColors() {
    final var scheme = EditorColorsManager.getInstance().getGlobalScheme();
    myComponentsWithEditorBackground.forEach(component -> {
      component.setBackground(scheme.getDefaultBackground());
    });

    final var borderTopBottom = JBUI.Borders.customLine(JBUI.CurrentTheme.Editor.BORDER_COLOR, 1, 0, 1, 0);
    if (myEditConfigOnly) {
      final var borderTopOnly = JBUI.Borders.customLine(JBUI.CurrentTheme.Editor.BORDER_COLOR, 1, 0, 0, 0);
      if (mySearchWrapper != null) mySearchWrapper.setBorder(myReplace ? borderTopBottom : borderTopOnly);
      if (myReplaceWrapper != null) myReplaceWrapper.setBorder(borderTopOnly);
    } else {
      if (mySearchWrapper != null) mySearchWrapper.setBorder(borderTopBottom);
      if (myReplaceWrapper != null) myReplaceWrapper.setBorder(borderTopBottom);
    }

    myExistingTemplatesComponent.updateColors();
  }

  private void exportToClipboard() {
    CopyPasteManager.getInstance().setContents(new TextTransferable(ConfigurationUtil.toXml(getConfiguration())));
  }

  private void importFromClipboard() {
    final String contents = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    if (!loadConfiguration(contents)) {
      reportMessage(SSRBundle.message("no.template.found.warning"), false, myOptionsToolbar);
    }
  }

  private static class ErrorBorder implements Border {
    private final Border myErrorBorder;

    ErrorBorder(Border errorBorder) {
      myErrorBorder = errorBorder;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      final EditorTextField editorTextField = ComponentUtil.getParentOfType((Class<? extends EditorTextField>)EditorTextField.class, c);
      if (editorTextField == null) {
        return;
      }
      final Object object = editorTextField.getClientProperty("JComponent.outline");
      if ("error".equals(object) || "warning".equals(object)) {
        myErrorBorder.paintBorder(c, g, x, y, width, height);
      }
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return myErrorBorder.getBorderInsets(c);
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }
  }

  private class SwitchAction extends AnAction implements DumbAware {

    SwitchAction() {
      init();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      storeDimensions();
      myReplace = !myReplace;
      setTitle(getDefaultTitle());
      myReplacePanel.setVisible(myReplace);
      updateColors();
      setUseLastConfiguration(true);
      loadConfiguration(myConfiguration);
      final Dimension size =
        DimensionService.getInstance().getSize(myReplace ? REPLACE_DIMENSION_SERVICE_KEY : SEARCH_DIMENSION_SERVICE_KEY, e.getProject());
      if (size != null) {
        setSize(getSize().width, size.height);
      }
      else {
        pack();
      }
      init();
    }

    private void init() {
      getTemplatePresentation().setText(SSRBundle.messagePointer(myReplace ? "switch.to.search.action" : "switch.to.replace.action"));
      getTemplatePresentation().setIcon(AllIcons.Actions.Refresh);
      final ActionManager actionManager = ActionManager.getInstance();
      final ShortcutSet searchShortcutSet = actionManager.getAction("StructuralSearchPlugin.StructuralSearchAction").getShortcutSet();
      final ShortcutSet replaceShortcutSet = actionManager.getAction("StructuralSearchPlugin.StructuralReplaceAction").getShortcutSet();
      final ShortcutSet shortcutSet = myReplace
                                      ? new CompositeShortcutSet(searchShortcutSet, replaceShortcutSet)
                                      : new CompositeShortcutSet(replaceShortcutSet, searchShortcutSet);
      registerCustomShortcutSet(shortcutSet, getRootPane());
    }
  }

  private class MyEditorTextField extends EditorTextField {
    private final boolean myReplace;

    MyEditorTextField(Document document, boolean replace) {
      super(document, StructuralSearchDialog.this.getProject(), StructuralSearchDialog.this.myFileType, false, false);
      myReplace = replace;
    }

    @Override
    protected @NotNull EditorEx createEditor() {
      final EditorEx editor = super.createEditor();
      editor.setHorizontalScrollbarVisible(true);
      editor.setVerticalScrollbarVisible(true);
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
      if (profile != null) {
        TemplateEditorUtil.setHighlighter(editor, UIUtil.getTemplateContextType(profile));
      }
      SubstitutionShortInfoHandler.install(editor, myFilterPanel, variableName -> {
        if (variableName.endsWith(ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX)) {
          //noinspection AssignmentToLambdaParameter
          variableName = StringUtil.trimEnd(variableName, ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX);
          assert myConfiguration instanceof ReplaceConfiguration;
          myFilterPanel.initFilters(UIUtil.getOrAddReplacementVariable(variableName, myConfiguration));
        }
        else{
          myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(variableName, myConfiguration));
        }
        if (isFilterPanelVisible()) {
          myConfiguration.setCurrentVariableName(variableName);
        }
      }, myDisposable, myReplace);
      editor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
      getDocument().putUserData(STRUCTURAL_SEARCH_ERROR_CALLBACK, () -> {
        if (getClientProperty("JComponent.outline") == null) initValidation();
      });

      TextCompletionUtil.installCompletionHint(editor);
      editor.putUserData(STRUCTURAL_SEARCH_DIALOG, StructuralSearchDialog.this);
      editor.setEmbeddedIntoDialogWrapper(true);
      return editor;
    }

    @Override
    protected void updateBorder(@NotNull EditorEx editor) {
      setupBorder(editor);
      final JScrollPane scrollPane = editor.getScrollPane();
      scrollPane.setBorder(new ErrorBorder(scrollPane.getBorder()));
    }
  }
}
