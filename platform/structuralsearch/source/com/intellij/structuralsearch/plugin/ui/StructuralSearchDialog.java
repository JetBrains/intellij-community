// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.find.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
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
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceCommand;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.filters.FilterPanel;
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.textCompletion.TextCompletionUtil;
import com.intellij.util.ui.TextTransferable;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.text.StringUtil.*;

/**
 * This dialog is used in two ways:
 * 1. a non-modal search dialog, showing a scope panel
 * 2. a modal edit dialog for Structural Search inspection patterns
 *
 * @author Bas Leijdekkers
 */
public class StructuralSearchDialog extends DialogWrapper {
  @NonNls private static final String SEARCH_DIMENSION_SERVICE_KEY = "#com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog";
  @NonNls private static final String REPLACE_DIMENSION_SERVICE_KEY = "#com.intellij.structuralsearch.plugin.ui.StructuralReplaceDialog";

  @NonNls private static final String RECURSIVE_STATE = "structural.search.recursive";
  @NonNls private static final String MATCH_CASE_STATE = "structural.search.match.case";
  @NonNls private static final String SHORTEN_FQN_STATE = "structural.search.shorten.fqn";
  @NonNls private static final String REFORMAT_STATE = "structural.search.reformat";
  @NonNls private static final String USE_STATIC_IMPORT_STATE = "structural.search.use.static.import";
  @NonNls private static final String FILTERS_VISIBLE_STATE = "structural.search.filters.visible";

  public static final Key<StructuralSearchDialog> STRUCTURAL_SEARCH_DIALOG = Key.create("STRUCTURAL_SEARCH_DIALOG");
  public static final Key<String> STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID = Key.create("STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID");
  public static final String USER_DEFINED = SSRBundle.message("new.template.defaultname");

  private final SearchContext mySearchContext;
  Editor myEditor;
  boolean myReplace;
  Configuration myConfiguration;
  @NonNls LanguageFileType myFileType = StructuralSearchUtil.getDefaultFileType();
  Language myDialect = null;
  PatternContext myPatternContext = null;
  final List<RangeHighlighter> myRangeHighlighters = new SmartList<>();

  // ui management
  private final Alarm myAlarm;
  private boolean myUseLastConfiguration;
  private final boolean myEditConfigOnly;
  private boolean myDoingOkAction;

  // components
  JCheckBox myRecursive;
  private JCheckBox myMatchCase;
  private JCheckBox myShortenFQN; // replace
  private JCheckBox myReformat; // replace
  private JCheckBox myUseStaticImport; // replace
  FileTypeSelector myFileTypesComboBox;
  ActionToolbarImpl myOptionsToolbar;
  EditorTextField mySearchCriteriaEdit;
  EditorTextField myReplaceCriteriaEdit;
  OnePixelSplitter mySearchEditorPanel;
  private OnePixelSplitter myReplaceEditorPanel;

  FilterPanel myFilterPanel;
  private LinkComboBox myTargetComboBox;
  private ScopePanel myScopePanel;
  private JCheckBox myOpenInNewTab;

  JComponent myReplacePanel;

  public StructuralSearchDialog(SearchContext searchContext, boolean replace) {
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
    final FileEditorManagerListener listener = new FileEditorManagerListener() {

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        if (myRangeHighlighters.isEmpty()) return;
        removeMatchHighlights();
        myEditor = event.getManager().getSelectedTextEditor();
        addMatchHighlights();
      }
    };
    searchContext.getProject().getMessageBus().connect(getDisposable()).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
    myConfiguration = createConfiguration(null);
    setTitle(getDefaultTitle());

    init();
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
    ProjectManager.getInstance().addProjectManagerListener(searchContext.getProject(), new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        close(CANCEL_EXIT_CODE);
      }
    });
  }

  public void setUseLastConfiguration(boolean useLastConfiguration) {
    myUseLastConfiguration = useLastConfiguration;
  }

  private EditorTextField createEditor() {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    assert profile != null;
    final Document document = UIUtil.createDocument(getProject(), myFileType, myDialect, myPatternContext, "", profile);
    document.putUserData(STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID, (myPatternContext == null) ? "" : myPatternContext.getId());

    final EditorTextField textField = new EditorTextField(document, getProject(), myFileType, false, false) {
      @Override
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        editor.setHorizontalScrollbarVisible(true);
        editor.setVerticalScrollbarVisible(true);
        final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
        assert profile != null;
        TemplateEditorUtil.setHighlighter(editor, UIUtil.getTemplateContextType(profile));
        SubstitutionShortInfoHandler.install(editor, variableName -> {
          myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(variableName, myConfiguration));
          if (isFilterPanelVisible()) {
            myConfiguration.setCurrentVariableName(variableName);
          }
        });
        editor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
        if (profile.highlightProblemsInEditor()) {
          profile.setProblemCallback(() -> {
            mySearchCriteriaEdit.putClientProperty("JComponent.outline", "error");
            mySearchCriteriaEdit.repaint();
            getOKAction().setEnabled(false);
            removeMatchHighlights();
          });
        }
        else {
          final Project project = getProject();
          final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(getDocument());
          if (file != null) {
            DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file, false);
          }
        }

        TextCompletionUtil.installCompletionHint(editor);
        editor.putUserData(STRUCTURAL_SEARCH_DIALOG, StructuralSearchDialog.this);
        editor.setEmbeddedIntoDialogWrapper(true);
        return editor;
      }

      @Override
      protected void updateBorder(@NotNull EditorEx editor) {
        setupBorder(editor);
      }
    };
    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    textField.setFont(scheme.getFont(EditorFontType.PLAIN));
    textField.setPreferredSize(new Dimension(550, 150));
    textField.setMinimumSize(new Dimension(200, 50));
    textField.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull final DocumentEvent event) {
        initiateValidation();
      }
    });
    return textField;
  }

  void initiateValidation() {
    if (myAlarm.isDisposed()) return;
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> {
      final boolean success = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> {
        try {
          final CompiledPattern compiledPattern = compilePattern();
          final JRootPane component = getRootPane();
          if (component == null) {
            return;
          }
          initializeFilterPanel();
          if (compiledPattern != null) {
            addMatchHighlights();
          }
          ApplicationManager.getApplication().invokeLater(() -> {
            setSearchTargets(myConfiguration.getMatchOptions());
            getOKAction().setEnabled(compiledPattern != null);
          }, ModalityState.stateForComponent(component));
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (RuntimeException e) {
          Logger.getInstance(StructuralSearchDialog.class).error(e);
        }
      });
      if (!success) {
        initiateValidation();
      }
    }, 100);
  }

  private void initializeFilterPanel() {
    final MatchOptions matchOptions = getConfiguration().getMatchOptions();
    final CompiledPattern compiledPattern = PatternCompiler.compilePattern(getProject(), matchOptions, false, false);
    ApplicationManager.getApplication().invokeLater(() -> {
      if (compiledPattern != null) {
        final SubstitutionShortInfoHandler handler = SubstitutionShortInfoHandler.retrieve(mySearchCriteriaEdit.getEditor());
        if (handler != null) {
          handler.updateEditorInlays();
        }
        myFilterPanel.setCompiledPattern(compiledPattern);
      }
      if (!myFilterPanel.isInitialized()) {
        myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(Configuration.CONTEXT_VAR_NAME, myConfiguration));
      }
      myFilterPanel.setValid(compiledPattern != null);
    }, ModalityState.stateForComponent(myFilterPanel.getComponent()));
  }

  private Configuration createConfiguration(Configuration template) {
    if (myReplace) {
      return (template == null) ? new ReplaceConfiguration(USER_DEFINED, USER_DEFINED) : new ReplaceConfiguration(template);
    }
    else {
      return (template == null) ? new SearchConfiguration(USER_DEFINED, USER_DEFINED) : new SearchConfiguration(template);
    }
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
        myScopePanel.setScopesFromContext();
        ApplicationManager.getApplication().invokeLater(() -> startTemplate());
        return;
      }
    }

    final Configuration configuration = ConfigurationManager.getInstance(getProject()).getMostRecentConfiguration();
    if (configuration != null) {
      loadConfiguration(configuration);
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

    WriteCommandAction.runWriteCommandAction(project, "Adjust line indent", "Structural Search",
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
  String getDefaultTitle() {
    return myReplace ? SSRBundle.message("structural.replace.title") : SSRBundle.message("structural.search.title");
  }

  @Override
  protected JComponent createCenterPanel() {
    mySearchEditorPanel = new OnePixelSplitter(false, 1.0f);
    mySearchEditorPanel.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE);
    mySearchEditorPanel.getDivider().setOpaque(false);
    mySearchCriteriaEdit = createEditor();
    mySearchEditorPanel.setFirstComponent(mySearchCriteriaEdit);
    mySearchEditorPanel.add(BorderLayout.CENTER, mySearchCriteriaEdit);

    myReplacePanel = createReplacePanel();
    myReplacePanel.setVisible(myReplace);

    myScopePanel = new ScopePanel(getProject(), myDisposable);
    if (!myEditConfigOnly) {
      myScopePanel.setRecentDirectories(FindInProjectSettings.getInstance(getProject()).getRecentDirectories());
      myScopePanel.setScopeConsumer(scope -> initiateValidation());
    }
    else {
      myScopePanel.setVisible(false);
    }

    myFilterPanel = new FilterPanel(getProject(), StructuralSearchUtil.getProfileByFileType(myFileType), getDisposable());
    myFilterPanel.setConstraintChangedCallback(() -> initiateValidation());
    myFilterPanel.getComponent().setMinimumSize(new Dimension(300, 50));
    mySearchEditorPanel.setSecondComponent(myFilterPanel.getComponent());

    final JLabel searchTargetLabel = new JLabel(SSRBundle.message("search.target.label"));
    myTargetComboBox = new LinkComboBox(SSRBundle.message("complete.match.variable.name"));
    myTargetComboBox.setItemConsumer(item -> {
      final MatchOptions matchOptions = myConfiguration.getMatchOptions();
      for (String name : matchOptions.getVariableConstraintNames()) {
        matchOptions.getVariableConstraint(name).setPartOfSearchResults(name.equals(item));
      }
      initiateValidation();
    });

    final JPanel centerPanel = new JPanel(null);
    final GroupLayout layout = new GroupLayout(centerPanel);
    centerPanel.setLayout(layout);
    layout.setHorizontalGroup(
      layout.createParallelGroup()
        .addComponent(mySearchEditorPanel)
        .addComponent(myReplacePanel)
        .addComponent(myScopePanel)
        .addGroup(layout.createSequentialGroup()
                    .addComponent(searchTargetLabel)
                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
                    .addComponent(myTargetComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
        )
    );
    layout.setVerticalGroup(
      layout.createSequentialGroup()
        .addComponent(mySearchEditorPanel)
        .addGap(2)
        .addComponent(myReplacePanel)
        .addComponent(myScopePanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
        .addGap(16)
        .addGroup(layout.createParallelGroup()
                    .addComponent(searchTargetLabel)
                    .addComponent(myTargetComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
        )
    );

    return centerPanel;
  }

  private JComponent createReplacePanel() {
    final JLabel label = new JLabel(SSRBundle.message("replacement.template.label"));
    myShortenFQN = new JCheckBox(SSRBundle.message("shorten.fully.qualified.names.checkbox"));
    myReformat = new JCheckBox(SSRBundle.message("reformat.checkbox"));
    myUseStaticImport = new JCheckBox(SSRBundle.message("use.static.import.checkbox"));
    myReplaceCriteriaEdit = createEditor();
    myReplaceEditorPanel = new OnePixelSplitter(false, 1.0f);
    myReplaceEditorPanel.setFirstComponent(myReplaceCriteriaEdit);

    final JPanel replacePanel = new JPanel(null);
    final GroupLayout layout = new GroupLayout(replacePanel);
    replacePanel.setLayout(layout);
    layout.setHorizontalGroup(
      layout.createParallelGroup()
        .addGroup(
          layout.createSequentialGroup()
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 28, 28)
            .addComponent(label)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 20, Integer.MAX_VALUE)
            .addComponent(myShortenFQN)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 15, 15)
            .addComponent(myReformat)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 15, 15)
            .addComponent(myUseStaticImport)
        )
        .addComponent(myReplaceEditorPanel)
    );
    layout.setVerticalGroup(
      layout.createSequentialGroup().
        addGroup(
          layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(label)
            .addComponent(myShortenFQN)
            .addComponent(myReformat)
            .addComponent(myUseStaticImport)
        )
        .addComponent(myReplaceEditorPanel)
    );

    return replacePanel;
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    final DefaultActionGroup historyActionGroup =
      new DefaultActionGroup(new DumbAwareAction("History", null, AllIcons.Actions.SearchWithHistory) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          final Object source = e.getInputEvent().getSource();
          if (!(source instanceof Component)) return;
          JBPopupFactory.getInstance()
            .createPopupChooserBuilder(ConfigurationManager.getInstance(getProject()).getHistoryConfigurations())
            .setRenderer(SimpleListCellRenderer.<Configuration>create((label, value, index) -> {
              if (value instanceof ReplaceConfiguration) {
                label.setIcon(AllIcons.Actions.Replace);
                label.setText(shortenTextWithEllipsis(collapseWhiteSpace(value.getMatchOptions().getSearchPattern()), 49, 0, true)
                              + " ⇒ "
                              + shortenTextWithEllipsis(collapseWhiteSpace(value.getReplaceOptions().getReplacement()), 49, 0, true));
              }
              else {
                label.setIcon(AllIcons.Actions.Find);
                label.setText(shortenTextWithEllipsis(collapseWhiteSpace(value.getMatchOptions().getSearchPattern()), 100, 0, true));
              }
            }))
            .setItemChosenCallback(c -> loadConfiguration(c))
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .createPopup()
            .showUnderneathOf((Component)source);
        }
      });
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionToolbarImpl historyToolbar =
      (ActionToolbarImpl)actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, historyActionGroup, true);
    historyToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    final JLabel label = new JLabel(SSRBundle.message("search.template"));
    UIUtil.installCompleteMatchInfo(label, () -> myConfiguration, link -> showFilterPanel(link));

    myRecursive = new JCheckBox(SSRBundle.message("recursive.matching.checkbox"), true);
    myRecursive.setVisible(!myReplace);
    myMatchCase = new JCheckBox(FindBundle.message("find.popup.case.sensitive"), true);
    myFileType = UIUtil.detectFileType(mySearchContext);
    myDialect = myFileType.getLanguage();
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    if (profile != null) {
      final List<PatternContext> contexts = profile.getPatternContexts();
      if (!contexts.isEmpty()) {
        myPatternContext = contexts.get(0);
      }
    }
    myFileTypesComboBox = new FileTypeSelector();
    myFileTypesComboBox.setMinimumAndPreferredWidth(200);
    myFileTypesComboBox.setSelectedItem(myFileType, myDialect, myPatternContext);
    myFileTypesComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          final FileTypeInfo item = myFileTypesComboBox.getSelectedItem();
          if (item == null) return;
          myFileType = item.getFileType();
          myDialect = item.getDialect();
          myPatternContext = item.getContext();
          final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
          assert profile != null;
          final Document searchDocument =
            UIUtil.createDocument(getProject(), myFileType, myDialect, myPatternContext, mySearchCriteriaEdit.getText(), profile);
          mySearchCriteriaEdit.setNewDocumentAndFileType(myFileType, searchDocument);
          final String contextId = (myPatternContext == null) ? "" : myPatternContext.getId();
          searchDocument.putUserData(STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID, contextId);
          final Document replaceDocument =
            UIUtil.createDocument(getProject(), myFileType, myDialect, myPatternContext, myReplaceCriteriaEdit.getText(), profile);
          myReplaceCriteriaEdit.setNewDocumentAndFileType(myFileType, replaceDocument);
          replaceDocument.putUserData(STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID, contextId);
          myFilterPanel.setProfile(profile);
          initiateValidation();
        }
      }
    });
    final JLabel fileTypeLabel = new JLabel(SSRBundle.message("search.dialog.file.type.label"));
    fileTypeLabel.setLabelFor(myFileTypesComboBox);
    final DefaultActionGroup templateActionGroup = new DefaultActionGroup(
      new DumbAwareAction(SSRBundle.message("save.template.text.button")) {

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ConfigurationManager.getInstance(getProject()).showSaveTemplateAsDialog(getConfiguration());
        }
      },
      new CopyConfigurationAction(),
      new PasteConfigurationAction(),
      new DumbAwareAction(SSRBundle.message("copy.existing.template.button")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          final SelectTemplateDialog dialog = new SelectTemplateDialog(getProject(), false, myReplace);
          if (!dialog.showAndGet()) {
            return;
          }
          final Configuration[] configurations = dialog.getSelectedConfigurations();
          if (configurations.length == 1) {
            loadConfiguration(configurations[0]);
          }
        }
      },
      Separator.getInstance(),
      new SwitchAction()
    );
    templateActionGroup.setPopup(true);
    templateActionGroup.getTemplatePresentation().setIcon(AllIcons.General.GearPlain);

    final AnAction filterAction = new DumbAwareToggleAction(null, "View variable filters", AllIcons.General.Filter) {

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return isFilterPanelVisible();
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        setFilterPanelVisible(state);
      }
    };
    final DefaultActionGroup optionsActionGroup = new DefaultActionGroup(filterAction, templateActionGroup);
    myOptionsToolbar = (ActionToolbarImpl)actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, optionsActionGroup, true);
    myOptionsToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myOptionsToolbar.setForceMinimumSize(true);

    final JPanel northPanel = new JPanel(null);
    final GroupLayout layout = new GroupLayout(northPanel);
    northPanel.setLayout(layout);
    layout.setHonorsVisibility(true);
    layout.setHorizontalGroup(
      layout.createSequentialGroup()
            .addComponent(historyToolbar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(label)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 20, Integer.MAX_VALUE)
            .addComponent(myRecursive)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 15, 15)
            .addComponent(myMatchCase)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 15, 15)
            .addComponent(fileTypeLabel)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
            .addComponent(myFileTypesComboBox, 125, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(myOptionsToolbar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(historyToolbar)
            .addComponent(label)
            .addComponent(myRecursive)
            .addComponent(myMatchCase)
            .addComponent(fileTypeLabel)
            .addComponent(myFileTypesComboBox)
            .addComponent(myOptionsToolbar)
    );

    return northPanel;
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

  Project getProject() {
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
    StructuralSearchPlugin.getInstance(getProject()).setDialogVisible(true);
    if (!myUseLastConfiguration) {
      setTextFromContext();
    }
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    setFilterPanelVisible(properties.getBoolean(FILTERS_VISIBLE_STATE, true));
    super.show();
  }

  private void startTemplate() {
    if (!Registry.is("ssr.template.from.selection.builder")) {
      return;
    }
    final Document document = mySearchCriteriaEdit.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
    assert psiFile != null;
    final TemplateBuilder builder = new StructuralSearchTemplateBuilder(psiFile).buildTemplate();
    WriteCommandAction
      .runWriteCommandAction(getProject(), "Live search template builder", "Structural Search",
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
    myDoingOkAction = true;
    final CompiledPattern compiledPattern = compilePattern();
    myDoingOkAction = false;
    if (compiledPattern == null) return;

    myAlarm.cancelAllRequests();
    myConfiguration.removeUnusedVariables();
    super.doOKAction();
    if (myEditConfigOnly) return;

    final SearchScope scope = myScopePanel.getScope();
    if (scope instanceof GlobalSearchScopesCore.DirectoryScope) {
      final GlobalSearchScopesCore.DirectoryScope directoryScope = (GlobalSearchScopesCore.DirectoryScope)scope;
      FindInProjectSettings.getInstance(getProject()).addDirectory(directoryScope.getDirectory().getPresentableUrl());
    }

    final FindSettings findSettings = FindSettings.getInstance();
    findSettings.setShowResultsInSeparateView(myOpenInNewTab.isSelected());

    try {
      ConfigurationManager.getInstance(getProject()).addHistoryConfiguration(myConfiguration);
      startSearching();
    }
    catch (MalformedPatternException ex) {
      reportMessage(SSRBundle.message("this.pattern.is.malformed.message", ex.getMessage()), true, mySearchCriteriaEdit);
    }
  }

  public Configuration getConfiguration() {
    saveConfiguration();
    return myConfiguration;
  }

  private CompiledPattern compilePattern() {
    final MatchOptions matchOptions = getConfiguration().getMatchOptions();
    final Project project = getProject();
    try {
      final CompiledPattern compiledPattern = PatternCompiler.compilePattern(project, matchOptions, true, !myEditConfigOnly);
      reportMessage(null, false, mySearchCriteriaEdit);
      if (myReplace) {
        try {
          Replacer.checkReplacementPattern(project, myConfiguration.getReplaceOptions());
        }
        catch (UnsupportedPatternException ex) {
          reportMessage(SSRBundle.message("unsupported.replacement.pattern.message", ex.getMessage()), true, myReplaceCriteriaEdit);
          return null;
        }
        catch (MalformedPatternException ex) {
          reportMessage(SSRBundle.message("malformed.replacement.pattern.message", ex.getMessage()), true, myReplaceCriteriaEdit);
          return null;
        }
      }
      reportMessage(null, false, myReplaceCriteriaEdit);
      return compiledPattern;
    }
    catch (MalformedPatternException e) {
      removeMatchHighlights();
      final String message = isEmpty(matchOptions.getSearchPattern())
                             ? null
                             : SSRBundle.message("this.pattern.is.malformed.message", (e.getMessage() != null) ? e.getMessage() : "");
      reportMessage(message, true, mySearchCriteriaEdit);
      return null;
    }
    catch (UnsupportedPatternException e) {
      removeMatchHighlights();
      reportMessage(SSRBundle.message("this.pattern.is.unsupported.message", e.getMessage()), true, mySearchCriteriaEdit);
      return null;
    }
    catch (NoMatchFoundException e) {
      removeMatchHighlights();
      reportMessage(e.getMessage(), false, myScopePanel);
      return null;
    }
  }

  void removeMatchHighlights() {
    if (myEditConfigOnly) {
      return;
    }
    final Editor editor = myEditor;
    if (editor == null) {
      return;
    }
    final Project project = getProject();
    ApplicationManager.getApplication().invokeLater(() -> {
      final HighlightManager highlightManager = HighlightManager.getInstance(project);
      for (RangeHighlighter highlighter : myRangeHighlighters) {
        highlightManager.removeSegmentHighlighter(editor, highlighter);
      }
      WindowManager.getInstance().getStatusBar(project).setInfo("");
      myRangeHighlighters.clear();
    });
  }

  void addMatchHighlights() {
    if (myEditConfigOnly) {
      return;
    }
    if (myDoingOkAction) {
      removeMatchHighlights();
    }
    else {
      final Project project = getProject();
      final Editor editor = myEditor;
      if (editor == null) {
        return;
      }
      final Document document = editor.getDocument();
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null) {
        return;
      }
      final MatchOptions matchOptions = getConfiguration().getMatchOptions();
      matchOptions.setScope(new LocalSearchScope(file, IdeBundle.message("scope.current.file")));
      final CollectingMatchResultSink sink = new CollectingMatchResultSink();
      new Matcher(project).findMatches(sink, matchOptions);
      final List<MatchResult> matches = sink.getMatches();
      removeMatchHighlights();
      addMatchHighlights(matches, editor, file, matches.size() + " results found in current file");
    }
  }

  private void addMatchHighlights(@NotNull List<MatchResult> matchResults,
                                  @NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @Nullable String statusBarText) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final Project project = getProject();
      if (project.isDisposed()) {
        return;
      }
      if (!matchResults.isEmpty()) {
        final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
        final TextAttributes textAttributes = globalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
        final HighlightManager highlightManager = HighlightManager.getInstance(project);
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
          highlightManager.addRangeHighlight(editor, start, end, textAttributes, false, myRangeHighlighters);
        }
        final FindManager findmanager = FindManager.getInstance(project);
        FindModel findmodel = findmanager.getFindNextModel();
        if (findmodel == null) {
          findmodel = findmanager.getFindInFileModel();
        }
        findmodel.setSearchHighlighters(true);
        findmanager.setFindWasPerformed();
        findmanager.setFindNextModel(findmodel);
      }
      WindowManager.getInstance().getStatusBar(project).setInfo(statusBarText);
    });
  }

  Balloon myBalloon = null;
  void reportMessage(@Nullable String message, boolean error, @NotNull JComponent component) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myBalloon != null) myBalloon.hide();
      component.putClientProperty("JComponent.outline", (!error || message == null) ? null : "error");
      component.repaint();

      if (message == null) return;
      myBalloon = JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(message, error ? MessageType.ERROR : MessageType.WARNING, null)
        .setHideOnFrameResize(false)
        .createBalloon();
      if (component != myScopePanel) {
        myBalloon.show(new RelativePoint(component, new Point(component.getWidth() / 2, component.getHeight())), Balloon.Position.below);
      }
      else {
        myBalloon.show(new RelativePoint(component, new Point(component.getWidth() / 2, 0)), Balloon.Position.above);
      }
      myBalloon.showInCenterOf(component);
      Disposer.register(myDisposable, myBalloon);
    }, ModalityState.stateForComponent(component));
  }

  void securityCheck() {
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    int scripts = 0;
    for (String name : matchOptions.getVariableConstraintNames()) {
      final MatchVariableConstraint constraint = matchOptions.getVariableConstraint(name);
      if (constraint.getScriptCodeConstraint().length() > 2) scripts++;
    }
    final ReplaceOptions replaceOptions = myConfiguration.getReplaceOptions();
    if (replaceOptions != null) {
      for (ReplacementVariableDefinition variableDefinition : replaceOptions.getVariableDefinitions()) {
        if (variableDefinition.getScriptCodeConstraint().length() > 2) scripts++;
      }
    }
    if (scripts > 0) {
      UIUtil.SSR_NOTIFICATION_GROUP.createNotification(NotificationType.WARNING)
        .setTitle(SSRBundle.message("import.template.script.warning.title"))
        .setContent(SSRBundle.message("import.template.script.warning", ApplicationNamesInfo.getInstance().getFullProductName(), scripts))
        .notify(mySearchContext.getProject());
    }
  }

  public void showFilterPanel(String variableName) {
    myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(variableName, myConfiguration));
    setFilterPanelVisible(true);
    myConfiguration.setCurrentVariableName(variableName);
  }

  void setFilterPanelVisible(boolean visible) {
    if (visible) {
      if (!myFilterPanel.isInitialized()) {
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

  boolean isFilterPanelVisible() {
    return mySearchEditorPanel.getSecondComponent() != null;
  }

  private void setSearchTargets(MatchOptions matchOptions) {
    final List<String> names = new ArrayList<>(matchOptions.getUsedVariableNames());
    Collections.sort(names);
    names.remove(Configuration.CONTEXT_VAR_NAME);
    names.add(SSRBundle.message("complete.match.variable.name"));
    myTargetComboBox.setItems(names);
    if (names.size() > 1) {
      myTargetComboBox.setEnabled(true);
      for (String name : names) {
        final MatchVariableConstraint constraint = matchOptions.getVariableConstraint(name);
        if (constraint != null && constraint.isPartOfSearchResults()) {
          myTargetComboBox.setSelectedItem(name);
          return;
        }
      }
      myTargetComboBox.setSelectedItem(SSRBundle.message("complete.match.variable.name"));
    }
    else {
      myTargetComboBox.setEnabled(false);
    }
  }

  /**
   * @param text  the text to try and load a configuration from
   * @return {@code true}, if some configuration was found, even if it was broken or corrupted {@code false} otherwise.
   */
  boolean loadConfiguration(String text) {
    if (text == null) {
      return false;
    }
    try {
      final Configuration configuration = ConfigurationUtil.fromXml(text);
      if (configuration == null) {
        return false;
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
    myConfiguration = createConfiguration(configuration);
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    setSearchTargets(matchOptions);
    if (!myEditConfigOnly) {
      myScopePanel.setScopesFromContext();
      final SearchScope scope = matchOptions.getScope();
      if (scope != null) myScopePanel.setScope(scope);
    }

    UIUtil.setContent(mySearchCriteriaEdit, matchOptions.getSearchPattern());

    myMatchCase.setSelected(matchOptions.isCaseSensitiveMatch());
    myFileTypesComboBox.setSelectedItem(matchOptions.getFileType(), matchOptions.getDialect(), matchOptions.getPatternContext());
    final Editor searchEditor = mySearchCriteriaEdit.getEditor();
    if (searchEditor != null) {
      searchEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
    }

    final PropertiesComponent properties = PropertiesComponent.getInstance();
    if (myReplace) {
      if (configuration instanceof ReplaceConfiguration) {
        final ReplaceOptions replaceOptions = configuration.getReplaceOptions();

        UIUtil.setContent(myReplaceCriteriaEdit, replaceOptions.getReplacement());

        myShortenFQN.setSelected(replaceOptions.isToShortenFQN());
        myReformat.setSelected(replaceOptions.isToReformatAccordingToStyle());
        myUseStaticImport.setSelected(replaceOptions.isToUseStaticImport());
      }
      else {
        UIUtil.setContent(myReplaceCriteriaEdit, matchOptions.getSearchPattern());

        myShortenFQN.setSelected(properties.getBoolean(SHORTEN_FQN_STATE));
        myReformat.setSelected(properties.getBoolean(REFORMAT_STATE));
        myUseStaticImport.setSelected(properties.getBoolean(USE_STATIC_IMPORT_STATE));
      }
      final Editor replaceEditor = myReplaceCriteriaEdit.getEditor();
      if (replaceEditor != null) {
        replaceEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
      }
      myRecursive.setSelected(false);
    }
    else {
      if (configuration instanceof ReplaceConfiguration) {
        myRecursive.setSelected(properties.getBoolean(RECURSIVE_STATE));
      }
    }
  }

  private void saveConfiguration() {
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();

    if (!myEditConfigOnly) {
      final SearchScope scope = myScopePanel.getScope();
      final boolean searchWithinHierarchy = IdeBundle.message("scope.class.hierarchy").equals(scope.getDisplayName());
      // We need to reset search within hierarchy scope during online validation since the scope works with user participation
      matchOptions.setScope(searchWithinHierarchy && !myDoingOkAction ? GlobalSearchScope.projectScope(getProject()) : scope);
    }
    matchOptions.setFileType(myFileType);
    matchOptions.setDialect(myDialect);
    matchOptions.setPatternContext(myPatternContext);
    matchOptions.setSearchPattern(getPattern(mySearchCriteriaEdit));
    matchOptions.setCaseSensitiveMatch(myMatchCase.isSelected());

    if (myReplace) {
      final ReplaceOptions replaceOptions = myConfiguration.getReplaceOptions();

      replaceOptions.setReplacement(getPattern(myReplaceCriteriaEdit));
      replaceOptions.setToShortenFQN(myShortenFQN.isSelected());
      replaceOptions.setToReformatAccordingToStyle(myReformat.isSelected());
      replaceOptions.setToUseStaticImport(myUseStaticImport.isSelected());
      matchOptions.setRecursiveSearch(false);
    }
    else {
      matchOptions.setRecursiveSearch(myRecursive.isSelected());
    }

    final PropertiesComponent properties = PropertiesComponent.getInstance();
    if (myReplace) {
      properties.setValue(SHORTEN_FQN_STATE, myShortenFQN.isSelected());
      properties.setValue(REFORMAT_STATE, myReformat.isSelected());
      properties.setValue(USE_STATIC_IMPORT_STATE, myUseStaticImport.isSelected());
    }
    else {
      properties.setValue(RECURSIVE_STATE, myRecursive.isSelected());
      properties.setValue(MATCH_CASE_STATE, myMatchCase.isSelected());
    }
  }

  private String getPattern(EditorTextField textField) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    assert profile != null;
    final Document document = textField.getDocument();
    final String pattern = ReadAction.compute(() -> {
      final PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
      assert file != null;
      return profile.getCodeFragmentText(file);
    });
    return pattern.isEmpty() ? textField.getText() : pattern;
  }

  @Nullable
  @Override
  protected final String getDimensionServiceKey() {
    return null;
  }

  @Override
  public void dispose() {
    if (myReplace) storeDimensions(REPLACE_DIMENSION_SERVICE_KEY, SEARCH_DIMENSION_SERVICE_KEY);
    else storeDimensions(SEARCH_DIMENSION_SERVICE_KEY, REPLACE_DIMENSION_SERVICE_KEY);

    final PropertiesComponent properties = PropertiesComponent.getInstance();
    properties.setValue(FILTERS_VISIBLE_STATE, isFilterPanelVisible(), true);
    StructuralSearchPlugin.getInstance(getProject()).setDialogVisible(false);
    myAlarm.cancelAllRequests();
    mySearchCriteriaEdit.removeNotify();
    myReplaceCriteriaEdit.removeNotify();
    super.dispose();
  }

  private void storeDimensions(String key1, String key2) {
    if (myEditConfigOnly) return; // don't store dimensions when editing structural search inspection patterns
    // handle own dimension service to store dimensions correctly when switching between search/replace in the same dialog
    final Dimension size = getSize();
    final DimensionService dimensionService = DimensionService.getInstance();
    final Point location = getLocation();
    if (location.x < 0) location.x = 0;
    if (location.y < 0) location.y = 0;
    dimensionService.setLocation(SEARCH_DIMENSION_SERVICE_KEY, location, getProject());
    dimensionService.setSize(key1, size, getProject());
    final Dimension otherSize = dimensionService.getSize(key2);
    if (otherSize != null && otherSize.width != size.width) {
      otherSize.width = size.width;
      dimensionService.setSize(key2, otherSize, getProject());
    }
  }

  @Override
  protected String getHelpId() {
    return "find.structuredSearch";
  }

  private class SwitchAction extends AnAction implements DumbAware {

    SwitchAction() {
      init();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myReplace = !myReplace;
      setTitle(getDefaultTitle());
      myReplacePanel.setVisible(myReplace);
      myRecursive.setVisible(!myReplace);
      loadConfiguration(myConfiguration);
      final Dimension size =
        DimensionService.getInstance().getSize(myReplace ? REPLACE_DIMENSION_SERVICE_KEY : SEARCH_DIMENSION_SERVICE_KEY);
      if (size != null) {
        setSize(getSize().width, size.height);
      }
      else {
        pack();
      }
      init();
    }

    private void init() {
      getTemplatePresentation().setText(SSRBundle.message(myReplace ? "switch.to.search.action" : "switch.to.replace.action"));
      final ActionManager actionManager = ActionManager.getInstance();
      final ShortcutSet searchShortcutSet = actionManager.getAction("StructuralSearchPlugin.StructuralSearchAction").getShortcutSet();
      final ShortcutSet replaceShortcutSet = actionManager.getAction("StructuralSearchPlugin.StructuralReplaceAction").getShortcutSet();
      final ShortcutSet shortcutSet = myReplace
                                      ? new CompositeShortcutSet(searchShortcutSet, replaceShortcutSet)
                                      : new CompositeShortcutSet(replaceShortcutSet, searchShortcutSet);
      registerCustomShortcutSet(shortcutSet, getRootPane());
    }
  }

  private class CopyConfigurationAction extends AnAction implements DumbAware {

    CopyConfigurationAction() {
      super(SSRBundle.message("export.template.action"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      CopyPasteManager.getInstance().setContents(new TextTransferable(ConfigurationUtil.toXml(myConfiguration)));
    }
  }

  private class PasteConfigurationAction extends AnAction implements DumbAware {

    PasteConfigurationAction() {
      super(SSRBundle.message("import.template.action"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final String contents = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
      if (!loadConfiguration(contents)) {
        reportMessage(SSRBundle.message("no.template.found.warning"), false, myOptionsToolbar);
      }
    }
  }
}
