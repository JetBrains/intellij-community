// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.codeInsight.template.impl.TemplateImplUtil;
import com.intellij.find.FindBundle;
import com.intellij.find.FindInProjectSettings;
import com.intellij.find.FindSettings;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
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
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.textCompletion.TextCompletionUtil;
import com.intellij.util.ui.LafIconLookup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchDialog extends DialogWrapper {
  @NonNls private static final String RECURSIVE_STATE = "structural.search.recursive";
  @NonNls private static final String MATCH_CASE_STATE = "structural.search.match.case";
  @NonNls private static final String SHORTEN_FQN_STATE = "structural.search.shorten.fqn";
  @NonNls private static final String REFORMAT_STATE = "structural.search.reformat";
  @NonNls private static final String USE_STATIC_IMPORT_STATE = "structural.search.use.static.import";

  public static final Key<StructuralSearchDialog> STRUCTURAL_SEARCH = Key.create("STRUCTURAL_SEARCH_AREA");
  public static final String USER_DEFINED = SSRBundle.message("new.template.defaultname");

  private final SearchContext mySearchContext;
  boolean myReplace;
  Configuration myConfiguration;
  @NonNls FileType myFileType = StructuralSearchUtil.getDefaultFileType();
  Language myDialect = null;
  String myContext = null;

  // ui management
  private final Alarm myAlarm;
  private boolean myUseLastConfiguration;
  private final boolean myEditConfigOnly;
  private boolean myDoingOkAction;
  boolean myFilterButtonEnabled = false;

  // components
  JCheckBox myRecursive;
  private JCheckBox myMatchCase;
  private JCheckBox myShortenFQN; // replace
  private JCheckBox myReformat; // replace
  private JCheckBox myUseStaticImport; // replace
  FileTypeSelector myFileTypesComboBox;
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

  public StructuralSearchDialog(SearchContext searchContext, boolean replace, boolean editConfigOnly) {
    super(searchContext.getProject(), true);

    if (!editConfigOnly) {
      setModal(false);
      setOKButtonText(FindBundle.message("find.dialog.find.button"));
    }
    myReplace = replace;
    myEditConfigOnly = editConfigOnly;
    mySearchContext = searchContext;
    myConfiguration = createConfiguration(null);
    setTitle(getDefaultTitle());

    init();
    registerSwitchActions(ActionManager.getInstance().getAction("StructuralSearchPlugin.StructuralSearchAction"), false);
    registerSwitchActions(ActionManager.getInstance().getAction("StructuralSearchPlugin.StructuralReplaceAction"), true);
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
    ProjectManager.getInstance().addProjectManagerListener(searchContext.getProject(), new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        close(CANCEL_EXIT_CODE);
      }
    });
  }

  private void registerSwitchActions(AnAction action, boolean replace) {
    new AnAction() {
      @Override
      public boolean isDumbAware() {
        return action.isDumbAware();
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (myReplace == replace) return;
        myReplace = replace;
        setTitle(getDefaultTitle());
        myReplacePanel.setVisible(replace);
        myRecursive.setVisible(!replace);
        loadConfiguration(myConfiguration);
        final Dimension size = DimensionService.getInstance().getSize(getDimensionServiceKey());
        if (size != null) {
          setSize(size.width, size.height);
        }
        else {
          pack();
        }
      }
    }.registerCustomShortcutSet(action.getShortcutSet(), getRootPane());
  }

  public void setUseLastConfiguration(boolean useLastConfiguration) {
    myUseLastConfiguration = useLastConfiguration;
  }

  void setSearchPattern(Configuration config) {
    loadConfiguration(config);
    initiateValidation();
  }

  private EditorTextField createEditor(String text) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    assert profile != null;
    final Document document = profile.createDocument(getProject(), myFileType, myDialect, text);
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull final DocumentEvent event) {
        initiateValidation();
      }
    });

    final EditorTextField textField = new EditorTextField(document, getProject(), myFileType, false, false) {
      @Override
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        TemplateEditorUtil.setHighlighter(editor, profile.getTemplateContextType());
        SubstitutionShortInfoHandler.install(editor, variableName ->
          myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(variableName, myConfiguration)));
        editor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
        final Project project = getProject();
        final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(getDocument());
        if (file != null) {
          DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file, false);
        }
        TextCompletionUtil.installCompletionHint(editor);
        editor.putUserData(STRUCTURAL_SEARCH, StructuralSearchDialog.this);
        editor.setEmbeddedIntoDialogWrapper(true);
        return editor;
      }

      @Override
      protected void updateBorder(@NotNull EditorEx editor) {
        setupBorder(editor);
      }
    };
    textField.setPreferredSize(new Dimension(850, 150));
    textField.setMinimumSize(new Dimension(200, 50));
    return textField;
  }

  void initiateValidation() {
    if (myAlarm.isDisposed()) return;
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> {
      try {
        final boolean valid = isValid();
        final boolean compiled = isCompiled();
        ApplicationManager.getApplication().invokeLater(() -> {
          myFilterButtonEnabled = compiled;
          setSearchTargets(myConfiguration.getMatchOptions());
          getOKAction().setEnabled(valid);
        }, ModalityState.stateForComponent(getRootPane()));
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (RuntimeException e) {
        Logger.getInstance(StructuralSearchDialog.class).error(e);
      }
    }, 250);
  }

  private boolean isCompiled() {
    try {
      final CompiledPattern compiledPattern = PatternCompiler.compilePattern(getProject(), myConfiguration.getMatchOptions(), false);
      if (compiledPattern != null) {
        myFilterPanel.setCompiledPattern(compiledPattern);
        if (!myFilterPanel.isInitialized()) {
          myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(Configuration.CONTEXT_VAR_NAME, myConfiguration));
        }
      }
      return compiledPattern != null;
    } catch (MalformedPatternException e) {
      return false;
    }
  }

  private void detectFileType() {
    PsiElement context = mySearchContext.getFile();

    final Editor editor = mySearchContext.getEditor();
    if (editor != null && context != null) {
      context = context.findElementAt(editor.getCaretModel().getOffset());
      if (context != null) {
        context = context.getParent();
      }
    }
    if (context != null) {
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(context);
      if (profile != null) {
        final FileType fileType = profile.detectFileType(context);
        if (fileType != null) {
          myFileType = fileType;
          return;
        }
      }
    }

    myFileType = StructuralSearchUtil.getDefaultFileType();
  }

  public Configuration createConfiguration(Configuration template) {
    if (myReplace) {
      return (template == null) ? new ReplaceConfiguration(USER_DEFINED, USER_DEFINED) : new ReplaceConfiguration(template);
    }
    else {
      return (template == null) ? new SearchConfiguration(USER_DEFINED, USER_DEFINED) : new SearchConfiguration(template);
    }
  }

  private void setText(String text) {
    setTextForEditor(text, mySearchCriteriaEdit);
    if (myReplace) {
      setTextForEditor(text, myReplaceCriteriaEdit);
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

    WriteCommandAction.writeCommandAction(project, file).run(
      () -> CodeStyleManager.getInstance(project).adjustLineIndent(file, new TextRange(0, document.getTextLength())));
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
    myReplaceEditorPanel = new OnePixelSplitter(false, 1.0f);
    mySearchEditorPanel.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE);
    mySearchEditorPanel.getDivider().setOpaque(false);
    mySearchCriteriaEdit = createEditor("");
    mySearchEditorPanel.setFirstComponent(mySearchCriteriaEdit);
    mySearchEditorPanel.add(BorderLayout.CENTER, mySearchCriteriaEdit);

    myReplacePanel = createReplacePanel();
    myReplacePanel.setVisible(myReplace);

    myScopePanel = new ScopePanel(getProject());
    if (!myEditConfigOnly) {
      myScopePanel.setRecentDirectories(FindInProjectSettings.getInstance(getProject()).getRecentDirectories());
      myScopePanel.setScopeConsumer(scope -> {
        if (scope == null) {
          getOKAction().setEnabled(false);
        }
        else {
          initiateValidation();
        }
      });
    }
    else {
      myScopePanel.setEnabled(false);
    }

    myFilterPanel = new FilterPanel(getProject(), StructuralSearchUtil.getProfileByFileType(myFileType), getDisposable());
    myFilterPanel.getComponent().setMinimumSize(new Dimension(300, 50));

    final JLabel searchTargetLabel = new JLabel(SSRBundle.message("search.target.label"));
    myTargetComboBox = new LinkComboBox(SSRBundle.message("complete.match.variable.name"));
    myTargetComboBox.setItemConsumer(item -> {
      final MatchOptions matchOptions = myConfiguration.getMatchOptions();
      for (String name : matchOptions.getVariableConstraintNames()) {
        matchOptions.getVariableConstraint(name).setPartOfSearchResults(name.equals(item));
      }
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
    myReplaceCriteriaEdit = createEditor("");
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
    final DefaultActionGroup historyActionGroup = new DefaultActionGroup(new AnAction(getShowHistoryIcon()) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        final Object source = e.getInputEvent().getSource();
        if (!(source instanceof Component)) return;
        JBPopupFactory.getInstance()
                      .createPopupChooserBuilder(ConfigurationManager.getInstance(getProject()).getHistoryConfigurations())
                      .setRenderer(new ColoredListCellRenderer<Configuration>() {
                        @Override
                        protected void customizeCellRenderer(@NotNull JList<? extends Configuration> list,
                                                             Configuration value,
                                                             int index,
                                                             boolean selected,
                                                             boolean hasFocus) {
                          if (value instanceof ReplaceConfiguration) {
                            setIcon(AllIcons.Actions.Replace);
                            append(StringUtil.shortenTextWithEllipsis(value.getMatchOptions().getSearchPattern(), 49, 0, true) +
                                   " ⇒ " + StringUtil.shortenTextWithEllipsis(value.getReplaceOptions().getReplacement(), 49, 0, true));
                          }
                          else {
                            setIcon(AllIcons.Actions.Find);
                            append(StringUtil.shortenTextWithEllipsis(value.getMatchOptions().getSearchPattern(), 100, 0, true));
                          }
                        }
                      })
                      .setItemChosenCallback(c -> setSearchPattern(c))
                      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
                      .createPopup()
                      .showUnderneathOf((Component)source);
      }
    });
    final ActionToolbarImpl historyToolbar =
      (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, historyActionGroup, true);
    historyToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    final JLabel label = new JLabel(SSRBundle.message("search.template"));
    UIUtil.installCompleteMatchInfo(label, () -> myConfiguration);

    myRecursive = new JCheckBox(SSRBundle.message("recursive.matching.checkbox"), true);
    myRecursive.setVisible(!myReplace);
    myMatchCase = new JCheckBox(FindBundle.message("find.popup.case.sensitive"), true);
    final List<FileType> types = new ArrayList<>();
    for (FileType fileType : StructuralSearchUtil.getSuitableFileTypes()) {
      if (StructuralSearchUtil.getProfileByFileType(fileType) != null) {
        types.add(fileType);
      }
    }
    Collections.sort(types, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
    detectFileType();
    myFileTypesComboBox = new FileTypeSelector(types);
    myFileTypesComboBox.setMinimumAndPreferredWidth(200);
    myFileTypesComboBox.setSelectedItem(myFileType, myDialect, myContext);
    myFileTypesComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          final FileTypeInfo item = myFileTypesComboBox.getSelectedItem();
          if (item == null) return;
          myFileType = item.getFileType();
          myDialect = item.getDialect();
          myContext = item.getContext();
          mySearchCriteriaEdit.setFileType(myFileType);
          myReplaceCriteriaEdit.setFileType(myFileType);
          final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
          myFilterPanel.setProfile(profile);
          initiateValidation();
        }
      }
    });
    final JLabel fileTypeLabel = new JLabel(SSRBundle.message("search.dialog.file.type.label"));
    fileTypeLabel.setLabelFor(myFileTypesComboBox);
    final DefaultActionGroup templateActionGroup = new DefaultActionGroup(
      new AnAction(SSRBundle.message("save.template.text.button")) {

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ConfigurationManager.getInstance(getProject()).showSaveTemplateAsDialog(getConfiguration());
        }
      },
      new AnAction(SSRBundle.message("copy.existing.template.button")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          final SelectTemplateDialog dialog = new SelectTemplateDialog(getProject(), false, myReplace);
          if (!dialog.showAndGet()) {
            return;
          }
          final Configuration[] configurations = dialog.getSelectedConfigurations();
          if (configurations.length == 1) {
            setSearchPattern(configurations[0]);
          }
        }
      }
    );
    templateActionGroup.setPopup(true);
    templateActionGroup.getTemplatePresentation().setIcon(AllIcons.General.GearPlain);

    final AnAction filterAction = new ToggleAction(null, "View variable filters", AllIcons.General.Filter) {

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return mySearchEditorPanel.getSecondComponent() != null;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        mySearchEditorPanel.setSecondComponent(state ? myFilterPanel.getComponent() : null);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(myFilterButtonEnabled);
        super.update(e);
      }
    };
    final DefaultActionGroup optionsActionGroup = new DefaultActionGroup(filterAction, templateActionGroup);
    final ActionToolbarImpl optionsToolbar =
      (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, optionsActionGroup, true);
    optionsToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    optionsToolbar.setForceMinimumSize(true);

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
            .addComponent(optionsToolbar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(historyToolbar)
            .addComponent(label)
            .addComponent(myRecursive)
            .addComponent(myMatchCase)
            .addComponent(fileTypeLabel)
            .addComponent(myFileTypesComboBox)
            .addComponent(optionsToolbar)
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

  private List<String> computeConfigurationVariableNames() {
    final List<String> result = new ArrayList<>(TemplateImplUtil.parseVariables(myConfiguration.getMatchOptions().getSearchPattern()).keySet());
    if (myReplace) {
      for (String var : TemplateImplUtil.parseVariables(myConfiguration.getReplaceOptions().getReplacement()).keySet()) {
        if (!result.contains(var)) {
          result.add(var + ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX);
        }
      }
    }
    return result;
  }

  private Project getProject() {
    return mySearchContext.getProject();
  }

  @Override
  public void show() {
    StructuralSearchPlugin.getInstance(getProject()).setDialogVisible(true);

    if (!myUseLastConfiguration) {
      final Editor editor = mySearchContext.getEditor();
      boolean setSomeText = false;

      if (editor != null) {
        final SelectionModel selectionModel = editor.getSelectionModel();

        if (selectionModel.hasSelection()) {
          setText(selectionModel.getSelectedText());
          myScopePanel.setScope(null);
          setSomeText = true;
        }
      }

      if (!setSomeText) {
        final Configuration configuration = ConfigurationManager.getInstance(getProject()).getMostRecentConfiguration();
        if (configuration != null) {
          loadConfiguration(configuration);
        }
      }
    }

    initiateValidation();
    super.show();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySearchCriteriaEdit;
  }

  @Override
  protected void doOKAction() {
    myDoingOkAction = true;
    final boolean result = isValid();
    myDoingOkAction = false;
    if (!result) return;

    myAlarm.cancelAllRequests();
    removeUnusedVariableConstraints();
    final SearchScope scope = myScopePanel.getScope();
    if (scope == null) return;
    super.doOKAction();
    if (myEditConfigOnly) return;

    if (scope instanceof GlobalSearchScopesCore.DirectoryScope) {
      final GlobalSearchScopesCore.DirectoryScope directoryScope = (GlobalSearchScopesCore.DirectoryScope)scope;
      FindInProjectSettings.getInstance(getProject()).addDirectory(directoryScope.getDirectory().getPresentableUrl());
    }

    final FindSettings findSettings = FindSettings.getInstance();
    findSettings.setDefaultScopeName(scope.getDisplayName());
    findSettings.setShowResultsInSeparateView(myOpenInNewTab.isSelected());

    try {
      ConfigurationManager.getInstance(getProject()).addHistoryConfiguration(myConfiguration);
      startSearching();
    }
    catch (MalformedPatternException ex) {
      reportMessage(SSRBundle.message("this.pattern.is.malformed.message", ex.getMessage()), true, mySearchCriteriaEdit);
    }
  }

  private void removeUnusedVariableConstraints() {
    final List<String> variableNames = computeConfigurationVariableNames();
    variableNames.add(Configuration.CONTEXT_VAR_NAME);
    myConfiguration.getMatchOptions().retainVariableConstraints(variableNames);
  }

  public Configuration getConfiguration() {
    removeUnusedVariableConstraints();
    saveConfiguration();
    return myConfiguration;
  }

  private boolean isValid() {
    final MatchOptions matchOptions = getConfiguration().getMatchOptions();
    try {
      Matcher.validate(getProject(), matchOptions);
    }
    catch (MalformedPatternException e) {
      final String message = StringUtil.isEmpty(matchOptions.getSearchPattern())
                             ? null
                             : SSRBundle.message("this.pattern.is.malformed.message", (e.getMessage() != null) ? e.getMessage() : "");
      reportMessage(message, true, mySearchCriteriaEdit);
      return false;
    }
    catch (UnsupportedPatternException e) {
      reportMessage(SSRBundle.message("this.pattern.is.unsupported.message", e.getMessage()), true, mySearchCriteriaEdit);
      return false;
    }
    catch (NoMatchFoundException e) {
      reportMessage(e.getMessage(), false, myScopePanel);
      return false;
    }
    reportMessage(null, false, mySearchCriteriaEdit);
    if (myReplace) {
      try {
        Replacer.checkSupportedReplacementPattern(getProject(), myConfiguration.getReplaceOptions());
      }
      catch (UnsupportedPatternException ex) {
        reportMessage(SSRBundle.message("unsupported.replacement.pattern.message", ex.getMessage()), true, myReplaceCriteriaEdit);
        return false;
      }
      catch (MalformedPatternException ex) {
        reportMessage(SSRBundle.message("malformed.replacement.pattern.message", ex.getMessage()), true, myReplaceCriteriaEdit);
        return false;
      }
    }
    reportMessage(null, false, myReplaceCriteriaEdit);
    return myEditConfigOnly || myScopePanel.getScope() != null;
  }

  private void reportMessage(String message, boolean error, JComponent component) {
    com.intellij.util.ui.UIUtil.invokeLaterIfNeeded(() -> {
      component.putClientProperty("JComponent.outline", (!error || message == null) ? null : "error");
      component.repaint();

      if (message == null) return;
      final Balloon balloon = JBPopupFactory.getInstance()
                                            .createHtmlTextBalloonBuilder(message, error ? MessageType.ERROR : MessageType.WARNING, null)
                                            .createBalloon();
      if (component == mySearchCriteriaEdit) {
        balloon.show(new RelativePoint(component, new Point(component.getWidth() / 2, component.getHeight())), Balloon.Position.below);
      }
      else {
        balloon.show(new RelativePoint(component, new Point(component.getWidth() / 2, 0)), Balloon.Position.above);
      }
      balloon.showInCenterOf(component);
      Disposer.register(myDisposable, balloon);
    });
  }

  public void showFilterPanel(String variableName) {
    myFilterPanel.initFilters(UIUtil.getOrAddVariableConstraint(variableName, myConfiguration));
    mySearchEditorPanel.setSecondComponent(myFilterPanel.getComponent());
  }

  private void setSearchTargets(MatchOptions matchOptions) {
    final List<String> names = new ArrayList<>(matchOptions.getVariableConstraintNames());
    names.remove(Configuration.CONTEXT_VAR_NAME);
    names.add(SSRBundle.message("complete.match.variable.name"));
    myTargetComboBox.setItems(names);
    if (names.size() > 1) {
      myTargetComboBox.setEnabled(true);
      for (String name : names) {
        final MatchVariableConstraint constraint = matchOptions.getVariableConstraint(name);
        if (constraint != null && constraint.isPartOfSearchResults()) {
          myTargetComboBox.setSelectedItem(name);
          break;
        }
      }
    }
    else {
      myTargetComboBox.setEnabled(false);
    }
  }

  public void loadConfiguration(Configuration configuration) {
    myConfiguration = createConfiguration(configuration);
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    setSearchTargets(matchOptions);
    final SearchScope scope = matchOptions.getScope();
    if (scope != null) {
      myScopePanel.setScope(scope);
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
      if (scope != null) {
        final boolean searchWithinHierarchy = IdeBundle.message("scope.class.hierarchy").equals(scope.getDisplayName());
        // We need to reset search within hierarchy scope during online validation since the scope works with user participation
        matchOptions.setScope(searchWithinHierarchy && !myDoingOkAction ? GlobalSearchScope.projectScope(getProject()) : scope);
      }
    }
    matchOptions.setFileType(myFileType);
    matchOptions.setDialect(myDialect);
    matchOptions.setPatternContext(myContext);
    matchOptions.setSearchPattern(mySearchCriteriaEdit.getDocument().getText());
    matchOptions.setCaseSensitiveMatch(myMatchCase.isSelected());

    if (myReplace) {
      final ReplaceOptions replaceOptions = myConfiguration.getReplaceOptions();

      replaceOptions.setReplacement(myReplaceCriteriaEdit.getDocument().getText());
      replaceOptions.setToShortenFQN(myShortenFQN.isSelected());
      replaceOptions.setToReformatAccordingToStyle(myReformat.isSelected());
      replaceOptions.setToUseStaticImport(myUseStaticImport.isSelected());
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

  @NotNull
  @Override
  protected String getDimensionServiceKey() {
    return myReplace
           ? "#com.intellij.structuralsearch.plugin.ui.StructuralReplaceDialog"
           : "#com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog";
  }

  @Override
  public void dispose() {
    StructuralSearchPlugin.getInstance(getProject()).setDialogVisible(false);
    myAlarm.cancelAllRequests();
    mySearchCriteriaEdit.removeNotify();
    myReplaceCriteriaEdit.removeNotify();
    super.dispose();
  }

  @Override
  protected String getHelpId() {
    return "find.structuredSearch";
  }

  private static Icon getShowHistoryIcon() {
    return ObjectUtils.coalesce(UIManager.getIcon("TextField.darcula.searchWithHistory.icon"), LafIconLookup.getIcon("searchWithHistory"));
  }
}
