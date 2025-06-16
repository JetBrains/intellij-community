// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.highlighting.HighlightHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.find.FindBundle;
import com.intellij.find.FindInProjectSettings;
import com.intellij.find.FindSettings;
import com.intellij.icons.AllIcons;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.lang.Language;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actions.IncrementalFindAction;
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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ValidationInfo;
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
import com.intellij.psi.util.PsiTreeUtil;
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
import com.intellij.ui.BadgeIconSupplier;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.textCompletion.TextCompletionUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBInsets;
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

import static com.intellij.structuralsearch.plugin.ui.StructuralSearchDialogKeys.*;
import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;
import static java.awt.GridBagConstraints.*;

/**
 * This dialog is used in two ways:
 * 1. a non-modal search dialog, showing a scope panel
 * 2. a modal edit dialog for Structural Search inspection patterns
 *
 * @author Bas Leijdekkers
 */
public final class StructuralSearchDialog extends DialogWrapper implements DocumentListener {
  private static final @NonNls String SEARCH_DIMENSION_SERVICE_KEY = "#com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog";
  private static final @NonNls String REPLACE_DIMENSION_SERVICE_KEY = "#com.intellij.structuralsearch.plugin.ui.StructuralReplaceDialog";

  private static final Key<Configuration> STRUCTURAL_SEARCH_PREVIOUS_CONFIGURATION = Key.create("STRUCTURAL_SEARCH_PREVIOUS_CONFIGURATION");

  private final @NotNull Project myProject;
  private final @NotNull SearchContext mySearchContext;
  private Editor myEditor;
  private ReplaceConfiguration myConfiguration;
  private @Nullable @NonNls LanguageFileType myFileType = StructuralSearchUtil.getDefaultFileType();
  private Language myDialect;
  private PatternContext myPatternContext;
  private final List<RangeHighlighter> myRangeHighlighters = new SmartList<>();
  private final DocumentListener myRestartHighlightingListener = new DocumentListener() {
    final Runnable runnable = () -> addMatchHighlights();

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
  private boolean myConfigurationLoaded;
  private volatile boolean myPinned;
  private final boolean myEditConfigOnly;
  private boolean myReplace;

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
    myProject = searchContext.getProject();
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
    final MessageBusConnection connection = myProject.getMessageBus().connect(getDisposable());
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
    connection.subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        if (project == myProject) {
          close(CANCEL_EXIT_CODE);
        }
      }
    });
    connection.subscribe(EditorColorsManager.TOPIC, uiSettings -> updateColors());
    setTitle(getDefaultTitle());

    init();
    loadUIState();
    if (!myConfigurationLoaded) {
      myConfiguration = createConfiguration(null);
    }
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

  private EditorTextField createEditor(boolean replace) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    assert profile != null;
    final Document document = UIUtil.createDocument(myProject, myFileType, myDialect, myPatternContext, "", profile);
    document.addDocumentListener(this, myDisposable);
    document.putUserData(STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID, (myPatternContext == null) ? "" : myPatternContext.getId());

    final EditorTextField textField = new MyEditorTextField(document, replace);
    textField.setFont(EditorFontType.getGlobalPlainFont().deriveFont(
      UISettingsUtils.getInstance().getScaledEditorFontSize()
    ));
    textField.setPreferredSize(new Dimension(550, 150));
    textField.setMinimumSize(new Dimension(200, 50));
    myComponentsWithEditorBackground.add(textField);
    return textField;
  }

  @Override
  public void documentChanged(final @NotNull DocumentEvent event) {
    initValidation();
    if (!myChangedConfiguration) {
      myExistingTemplatesComponent.templateChanged();
    }
  }

  private void initializeFilterPanel(@Nullable CompiledPattern compiledPattern) {
    final MatchOptions matchOptions = getConfiguration().getMatchOptions();
    final CompiledPattern finalCompiledPattern = compiledPattern == null
                                                 ? PatternCompiler.compilePattern(myProject, matchOptions, false, false)
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

  private @NotNull ReplaceConfiguration createConfiguration(Configuration template) {
    final ReplaceConfiguration result = template == null
                                        ? new ReplaceConfiguration(SSRBundle.message("new.template.defaultname"),
                                                                   SSRBundle.message("user.defined.category"))
                                        : new ReplaceConfiguration(template);
    if (!myEditConfigOnly) {
      final MatchOptions matchOptions = result.getMatchOptions();
      matchOptions.setSearchInjectedCode(myInjected.isSelected());
      matchOptions.setCaseSensitiveMatch(myMatchCase.isSelected());

      final ReplaceOptions replaceOptions = result.getReplaceOptions();
      replaceOptions.setToShortenFQN(myShortenFqn.isSelected());
      replaceOptions.setToUseStaticImport(myStaticImport.isSelected());
      replaceOptions.setToReformatAccordingToStyle(myReformat.isSelected());
    }
    return result;
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
        setTextForEditor(text, myReplaceCriteriaEdit);
        myScopePanel.setScopesFromContext(null);
        final Document document = editor.getDocument();
        final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (file != null) {
          final PsiElement start = file.findElementAt(selectionModel.getSelectionStart());
          final PsiElement end = file.findElementAt(selectionModel.getSelectionEnd() - 1);
          final PsiElement element = (start == null || end == null || start.getContainingFile() != end.getContainingFile())
                                     ? null
                                     : PsiTreeUtil.findCommonParent(start, end);
          final Language language = (element == null) ? file.getLanguage() : element.getLanguage();
          myFileTypeChooser.setSelectedItem(language.getAssociatedFileType(), language, null);
          if (language != file.getLanguage() && !myInjected.isSelected()) {
            myInjected.doClick();
          }
        }
        ApplicationManager.getApplication().invokeLater(() -> startTemplate());
        return;
      }
    }

    final Configuration previousConfiguration = myProject.getUserData(STRUCTURAL_SEARCH_PREVIOUS_CONFIGURATION);
    if (previousConfiguration != null) {
      loadConfiguration(previousConfiguration);
    }
    else {
      final Configuration configuration = ConfigurationManager.getInstance(myProject).getMostRecentConfiguration();
      if (configuration != null) {
        loadConfiguration(configuration);
      }
    }
  }

  private void setTextForEditor(String text, EditorTextField editor) {
    editor.setText(text);
    editor.selectAll();
    final Document document = editor.getDocument();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    documentManager.commitDocument(document);
    final PsiFile file = documentManager.getPsiFile(document);
    if (file == null) return;

    WriteCommandAction.runWriteCommandAction(myProject, SSRBundle.message("command.name.adjust.line.indent"), "Structural Search",
                                             () -> CodeStyleManager.getInstance(myProject)
                                               .adjustLineIndent(file, new TextRange(0, document.getTextLength())), file);
  }

  private void startSearching() {
    if (myReplace) {
      new ReplaceCommand(getConfiguration(), mySearchContext).startSearching();
    }
    else {
      new SearchCommand(getConfiguration(), mySearchContext).startSearching();
    }
  }

  private @NotNull @Nls @NlsContexts.DialogTitle String getDefaultTitle() {
    return myReplace ? SSRBundle.message("structural.replace.title") : SSRBundle.message("structural.search.title");
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected JComponent createCenterPanel() {
    final var searchPanel = createSearchPanel();
    myReplacePanel = createReplacePanel();
    myReplacePanel.setVisible(myReplace);

    myScopePanel = new ScopePanel(myProject, myDisposable);
    if (!myEditConfigOnly) {
      myScopePanel.setRecentDirectories(FindInProjectSettings.getInstance(myProject).getRecentDirectories());
      myScopePanel.setScopeConsumer(scope -> {
        if (myConfiguration != null) {
          myConfiguration.getMatchOptions().setScope(scope);
          initValidation();
        }
      });
    }
    else {
      myScopePanel.setVisible(false);
    }

    final var centerConstraint = new GridBag()
      .setDefaultFill(BOTH)
      .setDefaultWeightX(1.0)
      .setDefaultWeightY(1.0);
    final JPanel centerPanel = new JPanel(new GridBagLayout());
    centerPanel.add(searchPanel, centerConstraint.nextLine());
    centerPanel.add(myReplacePanel, centerConstraint.nextLine());
    centerPanel.add(myScopePanel, centerConstraint.nextLine().weighty(0.0));

    myExistingTemplatesComponent =
      new ExistingTemplatesComponent(myProject, getContentPanel(), new ImportConfigurationAction(), new ExportConfigurationAction());
    myExistingTemplatesComponent.onConfigurationSelected(this::loadConfiguration);
    myExistingTemplatesComponent.setConfigurationProducer(() -> getConfiguration());
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
      myFileType = info.getFileType();
      myDialect = info.getDialect();
      myPatternContext = info.getContext();
      final MatchOptions matchOptions = myConfiguration.getMatchOptions();
      matchOptions.setFileType(myFileType);
      matchOptions.setDialect(myDialect);
      matchOptions.setPatternContext(myPatternContext);

      myOptionsToolbar.updateActionsImmediately();
      myFilterPanel.setFileType(myFileType);
      final String contextId = (myPatternContext == null) ? "" : myPatternContext.getId();
      final StructuralSearchProfile profile1 = StructuralSearchUtil.getProfileByFileType(myFileType);

      final Document searchDocument =
        UIUtil.createDocument(myProject, myFileType, myDialect, myPatternContext, mySearchCriteriaEdit.getText(), profile1);
      searchDocument.addDocumentListener(this, myDisposable);
      mySearchCriteriaEdit.setNewDocumentAndFileType((myFileType == null) ? PlainTextFileType.INSTANCE : myFileType, searchDocument);
      searchDocument.putUserData(STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID, contextId);

      final Document replaceDocument =
        UIUtil.createDocument(myProject, myFileType, myDialect, myPatternContext, myReplaceCriteriaEdit.getText(), profile1);
      replaceDocument.addDocumentListener(this, myDisposable);
      myReplaceCriteriaEdit.setNewDocumentAndFileType((myFileType == null) ? PlainTextFileType.INSTANCE : myFileType, replaceDocument);
      replaceDocument.putUserData(STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID, contextId);

      updateOptions();
      initValidation();
    });
    myFileTypeChooser.setUserActionFileTypeInfoConsumer(info -> {
      myExistingTemplatesComponent.selectFileType(info.getFileType());
    });

    // Existing templates action
    final AnAction showTemplatesAction = new DumbAwareToggleAction(SSRBundle.message("templates.button"),
                                                                   SSRBundle.message("templates.button.description"),
                                                                   AllIcons.Actions.PreviewDetails) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }


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
    final BadgeIconSupplier filterIcon = new BadgeIconSupplier(AllIcons.General.Filter);
    final AnAction filterAction = new DumbAwareToggleAction(SSRBundle.message("filter.button"),
                                                            SSRBundle.message("filter.button.description"),
                                                            filterIcon.getLiveIndicatorIcon(true)) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        final Presentation presentation = e.getPresentation();
        presentation.setIcon(filterIcon.getLiveIndicatorIcon(myFilterPanel.hasVisibleFilter()));
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
    myFilterPanel = new FilterPanel(myProject, myFileType, getDisposable());
    myFilterPanel.setConstraintChangedCallback(() -> initValidation());
    myFilterPanel.getComponent().setMinimumSize(new Dimension(300, 50));

    // Pin action
    final DumbAwareToggleAction pinAction =
      new DumbAwareToggleAction(SSRBundle.message("pin.button"), SSRBundle.message("pin.button.description"), AllIcons.General.Pin_tab) {
        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.BGT;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
          return myPinned;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
          myPinned = state;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          super.update(e);
          e.getPresentation().setEnabledAndVisible(!myEditConfigOnly);
        }
      };

    final DefaultActionGroup optionsActionGroup =
      new DefaultActionGroup(myFileTypeChooser, showTemplatesAction, filterAction, new Separator(), pinAction, new SwitchAction());
    final ActionManager actionManager = ActionManager.getInstance();
    myOptionsToolbar = (ActionToolbarImpl)actionManager.createActionToolbar("StructuralSearchDialog", optionsActionGroup, true);
    myOptionsToolbar.setTargetComponent(mySearchCriteriaEdit);
    myOptionsToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    myOptionsToolbar.setForceMinimumSize(true);
    myOptionsToolbar.setBorder(JBUI.Borders.empty(DEFAULT_VGAP, 0));

    // Search editor panel, 1st splitter element
    final var searchEditorPanel = new JPanel(new GridBagLayout());

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
    myInjected.setVisible(!myEditConfigOnly);

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

    final var searchConstraint = new GridBag().setDefaultInsets(DEFAULT_VGAP, DEFAULT_HGAP, DEFAULT_VGAP, 0);
    searchEditorPanel.add(mySearchCriteriaEdit, searchConstraint
      .nextLine().fillCell().coverLine()
      .weightx(1.0).weighty(1.0)
      .emptyInsets());
    searchEditorPanel.add(searchTargetLabel, searchConstraint.nextLine());
    searchEditorPanel.add(myTargetComboBox, searchConstraint);
    searchEditorPanel.add(myInjected, searchConstraint);
    searchEditorPanel.add(myMatchCase, searchConstraint.anchor(WEST).insetRight(DEFAULT_HGAP));

    mySearchEditorPanel.setSecondComponent(myFilterPanel.getComponent());
    myComponentsWithEditorBackground.add(myFilterPanel.getTable());

    final JPanel searchPanel = new JPanel(new GridBagLayout());
    final var northConstraint = new GridBag().setDefaultWeightX(1.0);
    searchPanel.add(searchTemplateLabel, northConstraint.nextLine().weightx(0.0).insets(JBInsets.create(0, DEFAULT_HGAP)));
    searchPanel.add(myOptionsToolbar, northConstraint.anchor(EAST));
    searchPanel.add(mySearchWrapper, northConstraint.nextLine().coverLine().fillCell().emptyInsets().weighty(1.0));
    return searchPanel;
  }

  private @NotNull JComponent createReplacePanel() {
    final JBLabel replacementTemplateLabel = new JBLabel(SSRBundle.message("replacement.template.label"));

    myShortenFqn = new JBCheckBox(SSRBundle.message("shorten.fully.qualified.names.checkbox"));
    myShortenFqn.setOpaque(false);
    myShortenFqn.addActionListener(e -> myConfiguration.getReplaceOptions().setToShortenFQN(myShortenFqn.isSelected()));

    myStaticImport = new JBCheckBox(SSRBundle.message("use.static.import.checkbox"));
    myStaticImport.setOpaque(false);
    myStaticImport.addActionListener(e -> myConfiguration.getReplaceOptions().setToUseStaticImport(myStaticImport.isSelected()));

    myReformat = new JBCheckBox(SSRBundle.message("reformat.checkbox"));
    myReformat.setOpaque(false);
    myReformat.addActionListener(e -> myConfiguration.getReplaceOptions().setToReformatAccordingToStyle(myReformat.isSelected()));

    myReplaceCriteriaEdit = createEditor(true);
    myReplaceWrapper = new JPanel(new GridBagLayout());
    myComponentsWithEditorBackground.add(myReplaceWrapper);

    final var wrapperConstraint = new GridBag().setDefaultInsets(10, 10, 10, 0);
    myReplaceWrapper.add(myReplaceCriteriaEdit, wrapperConstraint.nextLine().emptyInsets().fillCell().coverLine().weightx(1.0).weighty(1.0));
    myReplaceWrapper.add(myShortenFqn, wrapperConstraint.nextLine());
    myReplaceWrapper.add(myStaticImport, wrapperConstraint);
    myReplaceWrapper.add(myReformat, wrapperConstraint.weightx(1.0).anchor(WEST));

    final JPanel replacePanel = new JPanel(new GridBagLayout());
    final var replaceConstraint = new GridBag()
      .setDefaultWeightX(1.0);
    replacePanel.add(replacementTemplateLabel, replaceConstraint.nextLine().anchor(WEST).insets(16, DEFAULT_HGAP, 14, 0));
    replacePanel.add(myReplaceWrapper, replaceConstraint.nextLine().fillCell().weighty(1.0));
    return replacePanel;
  }

  private void updateOptions() {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    myShortenFqn.setVisible(profile != null && profile.supportsShortenFQNames());
    myStaticImport.setVisible(profile != null && profile.supportsUseStaticImports());
  }

  @Override
  protected @Nullable JPanel createSouthAdditionalPanel() {
    if (myEditConfigOnly) return null;
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
    myOpenInNewTab = new JCheckBox(SSRBundle.message("open.in.new.tab.checkbox"));
    myOpenInNewTab.setSelected(FindSettings.getInstance().isShowResultsInSeparateView());
    panel.add(myOpenInNewTab, BorderLayout.EAST);
    return panel;
  }

  @Override
  public @Nullable Point getInitialLocation() {
    // handle dimension service manually to store dimensions correctly when switching between search/replace in the same dialog
    final DimensionService dimensionService = DimensionService.getInstance();
    final Dimension size = dimensionService.getSize(myReplace ? REPLACE_DIMENSION_SERVICE_KEY : SEARCH_DIMENSION_SERVICE_KEY, myProject);
    if (size != null) {
      setSize(size.width, myEditConfigOnly ? size.height - myScopePanel.getPreferredSize().height : size.height);
    }
    else {
      pack();
      // set width from replace if search not available and vice versa
      final Dimension otherSize =
        dimensionService.getSize(myReplace ? SEARCH_DIMENSION_SERVICE_KEY : REPLACE_DIMENSION_SERVICE_KEY, myProject);
      if (otherSize != null) {
        setSize(otherSize.width, getSize().height);
      }
    }
    if (myEditConfigOnly) return super.getInitialLocation();
    final Point location = dimensionService.getLocation(SEARCH_DIMENSION_SERVICE_KEY, myProject);
    return (location == null) ? super.getInitialLocation() : location;
  }

  @Override
  public void show() {
    if (!myConfigurationLoaded) {
      setTextFromContext();
    }
    super.show();
  }

  private void startTemplate() {
    if (!Registry.is("ssr.template.from.selection.builder")) {
      return;
    }
    final Document document = mySearchCriteriaEdit.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    assert psiFile != null;
    final TemplateBuilder builder = StructuralSearchTemplateBuilder.getInstance().buildTemplate(psiFile);
    if (builder == null) return;
    WriteCommandAction
      .runWriteCommandAction(myProject, SSRBundle.message("command.name.live.search.template.builder"), "Structural Search",
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
    if (scope instanceof GlobalSearchScopesCore.DirectoryScope directoryScope) {
      FindInProjectSettings.getInstance(myProject).addDirectory(directoryScope.getDirectory().getPresentableUrl());
    }

    FindSettings.getInstance().setShowResultsInSeparateView(myOpenInNewTab.isSelected());
    ConfigurationManager.getInstance(myProject).addHistoryConfiguration(getConfiguration());
    startSearching();
  }

  public Configuration getConfiguration() {
    saveConfiguration();
    //final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    //assert matchOptions.isCaseSensitiveMatch() == myMatchCase.isSelected();
    //assert matchOptions.isSearchInjectedCode() == myInjected.isSelected();
    //final ReplaceOptions replaceOptions = myConfiguration.getReplaceOptions();
    //assert replaceOptions.isToReformatAccordingToStyle() == myReformat.isSelected();
    //assert replaceOptions.isToUseStaticImport() == myStaticImport.isSelected();
    //assert replaceOptions.isToShortenFQN() == myShortenFqn.isSelected();
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
      if (myProject.isDisposed()) return;
      final HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      for (RangeHighlighter highlighter : myRangeHighlighters) {
        highlightManager.removeSegmentHighlighter(editor, highlighter);
      }
      WindowManager.getInstance().getStatusBar(myProject).setInfo("");
      myRangeHighlighters.clear();
    });
  }

  private void addMatchHighlights() {
    if (myEditConfigOnly) return;
    ReadAction.nonBlocking(() -> {
        if (DumbService.isDumb(myProject)) {
          // Search hits in the current editor are not shown when dumb.
          return null;
        }
        final Editor editor = myEditor;
        if (editor == null) {
          return null;
        }
        final Document document = editor.getDocument();
        final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (file == null) {
          return null;
        }
        final MatchOptions matchOptions = getConfiguration().getMatchOptions();
        matchOptions.setScope(new LocalSearchScope(file, PredefinedSearchScopeProviderImpl.getCurrentFileScopeName()));
        final CollectingMatchResultSink sink = new CollectingMatchResultSink();
        try {
          new Matcher(myProject, matchOptions).findMatches(sink);
          final List<MatchResult> matches = sink.getMatches();
          removeMatchHighlights();
          addMatchHighlights(matches, editor, file, SSRBundle.message("status.bar.text.results.found.in.current.file", matches.size()));
        }
        catch (MalformedPatternException | UnsupportedPatternException e) {
          reportMessage(e.getMessage().replace(ScriptSupport.UUID, ""), true, mySearchCriteriaEdit);
          removeMatchHighlights();
        }
        return null;
      })
      .withDocumentsCommitted(myProject)
      .expireWith(getDisposable())
      .coalesceBy(this)
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private void addMatchHighlights(@NotNull List<? extends MatchResult> matchResults,
                                  @NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NlsContexts.StatusBarText @Nullable String statusBarText) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) {
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
          final HighlightManager highlightManager = HighlightManager.getInstance(myProject);
          highlightManager.addRangeHighlight(editor, start, end, EditorColors.SEARCH_RESULT_ATTRIBUTES, false, myRangeHighlighters);
        }
        HighlightHandlerBase.setupFindModel(myProject);
      }
      WindowManager.getInstance().getStatusBar(myProject).setInfo(statusBarText);
    });
  }

  @Override
  protected boolean continuousValidation() {
    return false;
  }

  @Override
  protected @NotNull Alarm.ThreadToUse getContinuousValidationThreadToUse() {
    return Alarm.ThreadToUse.POOLED_THREAD;
  }

  @Override
  protected @NotNull List<ValidationInfo> doValidateAll() {
    final JRootPane component = getRootPane();
    if (component == null || myPerformAction) {
      return Collections.emptyList();
    }
    final List<ValidationInfo> errors = new SmartList<>();
    saveConfiguration();
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    try {
      CompiledPattern compiledPattern = null;
      try {
        compiledPattern = PatternCompiler.compilePattern(myProject, matchOptions, true, !myEditConfigOnly && !myPerformAction);
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
          Replacer.checkReplacementPattern(myProject, myConfiguration.getReplaceOptions());
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
      ApplicationManager.getApplication().invokeLater(() -> setSearchTargets(myConfiguration.getMatchOptions()),
                                                      ModalityState.stateForComponent(component));
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
    final ReplaceOptions replaceOptions = myConfiguration.getReplaceOptions();
    for (ReplacementVariableDefinition variableDefinition : replaceOptions.getVariableDefinitions()) {
      if (variableDefinition.getScriptCodeConstraint().length() > 2) scripts++;
    }
    if (scripts > 0) {
      NotificationGroupManager.getInstance()
        .getNotificationGroup(UIUtil.SSR_NOTIFICATION_GROUP_ID)
        .createNotification(
          SSRBundle.message("import.template.script.warning.title"),
          SSRBundle.message("import.template.script.warning", ApplicationNamesInfo.getInstance().getFullProductName(), scripts),
          NotificationType.WARNING
        )
        .notify(myProject);
    }
  }

  private void setFilterPanelVisible(boolean visible) {
    if (visible && myFilterPanel.getVariable() == null) {
      myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(Configuration.CONTEXT_VAR_NAME, myConfiguration));
    }
    if (isFilterPanelVisible() != visible) {
      mySearchEditorPanel.setSecondComponent(visible ? myFilterPanel.getComponent() : null);
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
        switchSearchReplace();
      }
      loadConfiguration(configuration);
      securityCheck();
    }
    catch (JDOMException e) {
      reportMessage(SSRBundle.message("import.template.script.corrupted") + '\n' + e.getMessage(), false, mySearchCriteriaEdit);
    }
    return true;
  }

  public void loadConfiguration(Configuration configuration) {
    myConfigurationLoaded = true;
    myConfiguration = createConfiguration(configuration);
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    final ReplaceOptions replaceOptions = myConfiguration.getReplaceOptions();
    if (myEditConfigOnly) {
      myMatchCase.setSelected(matchOptions.isCaseSensitiveMatch());
      myInjected.setSelected(false);
      myReformat.setSelected(replaceOptions.isToReformatAccordingToStyle());
      myStaticImport.setSelected(replaceOptions.isToUseStaticImport());
      myShortenFqn.setSelected(replaceOptions.isToShortenFQN());
    }
    setSearchTargets(matchOptions);

    myFileTypeChooser.setSelectedItem(matchOptions.getFileType(), matchOptions.getDialect(), matchOptions.getPatternContext());
    final Editor searchEditor = mySearchCriteriaEdit.getEditor();
    if (searchEditor != null) {
      searchEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
    }
    setEditorContent(false, matchOptions.getSearchPattern());

    final Editor replaceEditor = myReplaceCriteriaEdit.getEditor();
    if (replaceEditor != null) {
      replaceEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
    }
    setEditorContent(true, replaceOptions.getReplacement());
    updateOptions();
  }

  private void setEditorContent(boolean replace, String text) {
    myChangedConfiguration = true;
    UIUtil.setContent(replace ? myReplaceCriteriaEdit : mySearchCriteriaEdit, text);
    myChangedConfiguration = false;
  }

  private void saveConfiguration() {
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();

    if (myScopePanel.isVisible()) {
      final SearchScope scope = myScopePanel.getScope();
      final boolean searchWithinHierarchy = IdeBundle.message("scope.class.hierarchy").equals(scope.getDisplayName());
      // We need to reset search within hierarchy scope during online validation since the scope works with user participation
      matchOptions.setScope(searchWithinHierarchy && !myPerformAction ? GlobalSearchScope.projectScope(myProject) : scope);
    }
    else {
      matchOptions.setScope(null);
    }
    matchOptions.setSearchPattern(getPattern(mySearchCriteriaEdit));
    matchOptions.setRecursiveSearch(!myReplace);

    final ReplaceOptions replaceOptions = myConfiguration.getReplaceOptions();
    replaceOptions.setReplacement(getPattern(myReplaceCriteriaEdit));
  }

  private String getPattern(EditorTextField textField) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    if (profile != null) {
      final Document document = textField.getDocument();
      final String pattern = ReadAction.compute(() -> {
        final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        assert file != null;
        return profile.getCodeFragmentText(file);
      });
      return pattern.isEmpty() ? textField.getText() : pattern;
    }
    return textField.getText();
  }

  @Override
  public void dispose() {
    myProject.putUserData(STRUCTURAL_SEARCH_PREVIOUS_CONFIGURATION, myConfiguration);
    storeDimensions();

    saveUIState();
    StructuralSearchPlugin.getInstance(myProject).setDialog(null);
    myAlarm.cancelAllRequests();
    mySearchCriteriaEdit.removeNotify();
    myReplaceCriteriaEdit.removeNotify();
    removeRestartHighlightingListenerFromCurrentEditor();
    super.dispose();
  }

  private void loadUIState() {
    final UIState uiState = UIState.getInstance();
    if (isFilterPanelVisible() != uiState.filtersVisible) {
      mySearchEditorPanel.setSecondComponent(uiState.filtersVisible ? myFilterPanel.getComponent() : null);
    }
    setExistingTemplatesPanelVisible(uiState.existingTemplatesVisible);
    myExistingTemplatesComponent.setTreeState(uiState.templatesTreeState);
    if (!myEditConfigOnly) {
      myPinned = uiState.pinned;
      myInjected.setSelected(uiState.searchInjectedCode);
      myMatchCase.setSelected(uiState.matchCase);
      if (uiState.scopeDescriptor != null && uiState.scopeType != null) {
        myScopePanel.setScope(Scopes.createScope(myProject, uiState.scopeDescriptor, uiState.scopeType));
      }
      myShortenFqn.setSelected(uiState.shortenFQNames);
      myStaticImport.setSelected(uiState.useStaticImport);
      myReformat.setSelected(uiState.reformat);
    }
  }

  private void saveUIState() {
    if (mySearchEditorPanel == null) return;
    final UIState uiState = UIState.getInstance();
    uiState.filtersVisible = isFilterPanelVisible();
    uiState.existingTemplatesVisible = isExistingTemplatesPanelVisible();
    uiState.templatesTreeState = myExistingTemplatesComponent.getTreeState();

    if (!myEditConfigOnly) {
      uiState.pinned = myPinned;
      if (myInjected.isVisible()) {
        uiState.searchInjectedCode = myInjected.isSelected();
      }
      uiState.matchCase = myMatchCase.isSelected();
      if (myScopePanel.isVisible()) {
        final SearchScope scope = myScopePanel.getScope();
        uiState.scopeDescriptor = Scopes.getDescriptor(scope);
        uiState.scopeType = Scopes.getType(scope);
      }
      if (myReplace) {
        if (myShortenFqn.isVisible()) {
          uiState.shortenFQNames = myShortenFqn.isSelected();
        }
        if (myStaticImport.isVisible()) {
          uiState.useStaticImport = myStaticImport.isSelected();
        }
        uiState.reformat = myReformat.isSelected();
      }
    }
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
    dimensionService.setLocation(SEARCH_DIMENSION_SERVICE_KEY, location, myProject);
    final Dimension size = getSize();
    dimensionService.setSize(key1, size, myProject);
    final Dimension otherSize = dimensionService.getSize(key2, myProject);
    if (otherSize != null && otherSize.width != size.width) {
      otherSize.width = size.width;
      dimensionService.setSize(key2, otherSize, myProject);
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
    String text = ConfigurationUtil.toXml(getConfiguration());
    String html = "<html><body><pre><code>" + StringUtil.escapeXmlEntities(text) + "</code></pre></body></html>";
    CopyPasteManager.getInstance().setContents(new TextTransferable(html, text));
  }

  private void importFromClipboard() {
    final String contents = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    if (!loadConfiguration(contents)) {
      reportMessage(SSRBundle.message("no.template.found.warning"), false, mySearchCriteriaEdit);
    }
  }

  private void switchSearchReplace() {
    storeDimensions();
    myReplace = !myReplace;
    setTitle(getDefaultTitle());
    myReplacePanel.setVisible(myReplace);
    loadConfiguration(myConfiguration);
    final Dimension size =
      DimensionService.getInstance().getSize(myReplace ? REPLACE_DIMENSION_SERVICE_KEY : SEARCH_DIMENSION_SERVICE_KEY, myProject);
    if (size != null) {
      setSize(size.width, size.height);
    }
    else {
      pack();
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
      DarculaUIUtil.Outline object = DarculaUIUtil.getOutline(editorTextField);
      if (DarculaUIUtil.isWarningOrError(object)) {
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
      getTemplatePresentation().setIcon(AllIcons.Actions.Refresh);
      final ActionManager actionManager = ActionManager.getInstance();
      final ShortcutSet searchShortcutSet = actionManager.getAction("StructuralSearchPlugin.StructuralSearchAction").getShortcutSet();
      final ShortcutSet replaceShortcutSet = actionManager.getAction("StructuralSearchPlugin.StructuralReplaceAction").getShortcutSet();
      final ShortcutSet shortcutSet = myReplace
                                      ? new CompositeShortcutSet(searchShortcutSet, replaceShortcutSet)
                                      : new CompositeShortcutSet(replaceShortcutSet, searchShortcutSet);
      registerCustomShortcutSet(shortcutSet, getRootPane());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      switchSearchReplace();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setText(SSRBundle.messagePointer(myReplace ? "switch.to.search.action" : "switch.to.replace.action"));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  private class ExportConfigurationAction extends AnAction implements DumbAware {

    ExportConfigurationAction() {
      super(SSRBundle.messagePointer("export.template.action"), AllIcons.ToolbarDecorator.Export);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(!StringUtil.isEmptyOrSpaces(mySearchCriteriaEdit.getText()));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      exportToClipboard();
    }
  }

  private class ImportConfigurationAction extends AnAction implements DumbAware {

    ImportConfigurationAction() {
      super(SSRBundle.messagePointer("import.template.action"), AllIcons.ToolbarDecorator.Import);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      importFromClipboard();
    }
  }

  private class MyEditorTextField extends EditorTextField {
    private final boolean myReplace;

    MyEditorTextField(Document document, boolean replace) {
      super(document, myProject, myFileType, false, false);
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
          assert myReplace;
          myFilterPanel.initFilters(UIUtil.getOrAddReplacementVariable(variableName, myConfiguration));
        }
        else{
          myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(variableName, myConfiguration));
        }
      }, myDisposable, myReplace);
      editor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
      getDocument().putUserData(STRUCTURAL_SEARCH_ERROR_CALLBACK, () -> {
        if (getClientProperty("JComponent.outline") == null) initValidation();
      });

      TextCompletionUtil.installCompletionHint(editor);
      editor.putUserData(STRUCTURAL_SEARCH_DIALOG, StructuralSearchDialog.this);
      editor.setEmbeddedIntoDialogWrapper(true);
      editor.putUserData(IncrementalFindAction.SEARCH_DISABLED, Boolean.TRUE);
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
