// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.find.FindBundle;
import com.intellij.find.FindSettings;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.ui.EditorTextField;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.textCompletion.TextCompletionUtil;
import com.intellij.util.ui.LafIconLookup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *  Class to show the user the request for search
 */
public class StructuralSearchDialog extends DialogWrapper {
  static final Key<Boolean> STRUCTURAL_SEARCH = Key.create("STRUCTURAL_SEARCH_AREA");
  protected SearchContext searchContext;

  // text for search
  protected EditorTextField searchCriteriaEdit;

  // options of search scope
  private ScopeChooserCombo myScopeChooserCombo;

  private JCheckBox recursiveMatching;
  private JCheckBox caseSensitiveMatch;

  private FileTypeSelector fileTypes;

  protected Configuration myConfiguration;
  private JCheckBox openInNewTab;
  private final Alarm myAlarm;

  public static final String USER_DEFINED = SSRBundle.message("new.template.defaultname");
  private boolean useLastConfiguration;

  @NonNls private FileType ourFtSearchVariant = StructuralSearchUtil.getDefaultFileType();
  private static Language ourDialect = null;
  private static String ourContext = null;

  private final boolean myShowScopePanel;
  private final boolean myRunFindActionOnClose;
  private boolean myDoingOkAction;

  private String mySavedEditorText;
  private JPanel myContentPanel;
  private boolean myFilterEnabled = false;

  public StructuralSearchDialog(SearchContext searchContext) {
    this(searchContext, true, true);
  }

  public StructuralSearchDialog(SearchContext searchContext, boolean showScope, boolean runFindActionOnClose) {
    super(searchContext.getProject(), true);

    if (showScope) setModal(false);
    myShowScopePanel = showScope;
    myRunFindActionOnClose = runFindActionOnClose;
    this.searchContext = searchContext;
    setTitle(getDefaultTitle());

    if (runFindActionOnClose) {
      setOKButtonText(FindBundle.message("find.dialog.find.button"));
    }

    myConfiguration = createConfiguration(null);

    init();
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
    ProjectManager.getInstance().addProjectManagerListener(searchContext.getProject(), new ProjectManagerListener() {
      @Override
      public void projectClosing(Project project) {
        close(CANCEL_EXIT_CODE);
      }
    });
  }

  public void setUseLastConfiguration(boolean useLastConfiguration) {
    this.useLastConfiguration = useLastConfiguration;
  }

  void setSearchPattern(final Configuration config) {
    setValuesFromConfig(config);
    initiateValidation();
  }

  protected EditorTextField createEditor(final SearchContext searchContext, String text) {
    final FileTypeInfo info = fileTypes.getSelectedItem();
    assert info != null;
    final FileType fileType = info.getFileType();
    final Language dialect = info.getDialect();

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);
    assert profile != null;
    final Document document = profile.createDocument(searchContext.getProject(), fileType, dialect, text);
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(final DocumentEvent event) {
        initiateValidation();
      }
    });

    return new EditorTextField(document, searchContext.getProject(), fileType) {
      @Override
      protected EditorEx createEditor() {
        EditorEx editorEx = super.createEditor();
        TextCompletionUtil.installCompletionHint(editorEx);
        editorEx.putUserData(STRUCTURAL_SEARCH, true);
        return editorEx;
      }
    };
  }

  void initiateValidation() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> {
      try {
        final boolean valid = isValid();
        final boolean compiled = isCompiled();
        ApplicationManager.getApplication().invokeLater(() -> {
          myFilterEnabled = compiled;
          getOKAction().setEnabled(valid);
        });
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
      return PatternCompiler.compilePattern(getProject(), myConfiguration.getMatchOptions(), false) != null;
    } catch (MalformedPatternException e) {
      return false;
    }
  }

  void updateEditor() {
    if (myContentPanel != null) {
      if (searchCriteriaEdit != null) {
        mySavedEditorText = searchCriteriaEdit.getText();
        myContentPanel.remove(searchCriteriaEdit);
      }
      searchCriteriaEdit = createEditor(searchContext, mySavedEditorText != null ? mySavedEditorText : "");
      myContentPanel.add(searchCriteriaEdit, BorderLayout.CENTER);
      myContentPanel.revalidate();
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(ourFtSearchVariant);
      assert profile != null;

      searchCriteriaEdit.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          final Editor editor = searchCriteriaEdit.getEditor();
          if (editor == null) return;
          TemplateEditorUtil.setHighlighter(editor, profile.getTemplateContextType());
          SubstitutionShortInfoHandler.install(editor);
          editor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
          final Project project = searchContext.getProject();
          PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(searchCriteriaEdit.getDocument());
          if (psiFile != null) {
            DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, false);
          }
          searchCriteriaEdit.removePropertyChangeListener(this);
        }
      });

    }
  }

  private void detectFileTypeAndDialect() {
    final PsiFile file = searchContext.getFile();
    if (file != null) {
      PsiElement context = null;

      if (searchContext.getEditor() != null) {
        context = file.findElementAt(searchContext.getEditor().getCaretModel().getOffset());
        if (context != null) {
          context = context.getParent();
        }
      }
      if (context == null) {
        context = file;
      }

      FileType detectedFileType = null;

      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(context);
      if (profile != null) {
        FileType fileType = profile.detectFileType(context);
        if (fileType != null) {
          detectedFileType = fileType;
        }
      }

      if (detectedFileType == null) {
        for (FileType fileType : StructuralSearchUtil.getSuitableFileTypes()) {
          if (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage().equals(context.getLanguage())) {
            detectedFileType = fileType;
            break;
          }
        }
      }

      ourFtSearchVariant = detectedFileType != null ?
                           detectedFileType :
                           StructuralSearchUtil.getDefaultFileType();
    }
  }

  protected boolean isRecursiveSearchEnabled() {
    return myShowScopePanel;
  }

  public void setValuesFromConfig(Configuration configuration) {
    myConfiguration = createConfiguration(configuration);
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();

    //UIUtil.setContent(searchCriteriaEdit.getEditor(), matchOptions.getSearchPattern());
    final Document document = searchCriteriaEdit.getDocument();
    CommandProcessor.getInstance().executeCommand(searchContext.getProject(), () -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        document.replaceString(0, document.getTextLength(), matchOptions.getSearchPattern());
      });
    }, null, null);

    recursiveMatching.setSelected(isRecursiveSearchEnabled() && matchOptions.isRecursiveSearch());
    caseSensitiveMatch.setSelected(matchOptions.isCaseSensitiveMatch());

    fileTypes.setSelectedItem(matchOptions.getFileType(), matchOptions.getDialect(), matchOptions.getPatternContext());
    final Editor editor = searchCriteriaEdit.getEditor();
    if (editor != null) {
      editor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
    }
  }

  public Configuration createConfiguration(Configuration template) {
    return (template == null) ? new SearchConfiguration(USER_DEFINED, USER_DEFINED) : new SearchConfiguration(template);
  }

  protected void setText(String text) {
    setTextForEditor(text, searchCriteriaEdit);
  }

  protected final void setTextForEditor(final String selection, EditorTextField editor) {
    editor.setText(selection);
    editor.selectAll();
    final Project project = searchContext.getProject();
    final Document document = editor.getDocument();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    final PsiFile file = documentManager.getPsiFile(document);
    if (file == null) return;

    WriteCommandAction.writeCommandAction(project, file).run(() -> {
      CodeStyleManager.getInstance(project).adjustLineIndent(file, new TextRange(0, document.getTextLength()));
    });
  }

  protected void startSearching() {
    new SearchCommand(myConfiguration, searchContext).startSearching();
  }

  protected String getDefaultTitle() {
    return SSRBundle.message("structural.search.title");
  }

  @Override
  protected JComponent createCenterPanel() {
    myContentPanel = new JPanel(new BorderLayout());
    searchCriteriaEdit = createEditor(searchContext, mySavedEditorText != null ? mySavedEditorText : "");
    myContentPanel.add(BorderLayout.CENTER, searchCriteriaEdit);

    if (myShowScopePanel) { // todo remove me
      myScopeChooserCombo = new ScopeChooserCombo(
        searchContext.getProject(),
        true,
        false,
        FindSettings.getInstance().getDefaultScopeName()
      );
      Disposer.register(myDisposable, myScopeChooserCombo);
      myScopeChooserCombo.getComboBox().addItemListener(e -> initiateValidation());
    }
    else {
      myScopeChooserCombo = null;
    }

    JPanel scopePanel = new ScopePanel();
    myContentPanel.add(BorderLayout.SOUTH, scopePanel);

    updateEditor();
    return myContentPanel;
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    final DefaultActionGroup historyActionGroup = new DefaultActionGroup(new AnAction(getShowHistoryIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        SelectTemplateDialog dialog = new SelectTemplateDialog(searchContext.getProject(), true, isReplaceDialog());
        if (!dialog.showAndGet()) {
          return;
        }
        Configuration[] configurations = dialog.getSelectedConfigurations();
        if (configurations.length == 1) {
          setSearchPattern(configurations[0]);
          initiateValidation();
        }
      }
    });
    ActionToolbarImpl historyToolbar =
      (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, historyActionGroup, true);
    historyToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    final JLabel label = new JLabel(SSRBundle.message("search.template"));
    UIUtil.installCompleteMatchInfo(label, () -> myConfiguration);

    recursiveMatching = new JCheckBox(SSRBundle.message("recursive.matching.checkbox"), true);
    recursiveMatching.setVisible(isRecursiveSearchEnabled());
    caseSensitiveMatch = new JCheckBox(FindBundle.message("find.popup.case.sensitive"), true);
    final List<FileType> types = new ArrayList<>();
    for (FileType fileType : StructuralSearchUtil.getSuitableFileTypes()) {
      if (StructuralSearchUtil.getProfileByFileType(fileType) != null) {
        types.add(fileType);
      }
    }
    Collections.sort(types, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
    fileTypes = new FileTypeSelector(types);
    fileTypes.setMinimumAndPreferredWidth(200);
    fileTypes.setSelectedItem(ourFtSearchVariant, ourDialect, ourContext);
    fileTypes.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateEditor();
          initiateValidation();
        }
      }
    });
    final JLabel fileTypeLabel = new JLabel(SSRBundle.message("search.dialog.file.type.label"));
    fileTypeLabel.setLabelFor(fileTypes);
    final DefaultActionGroup templateActionGroup = new DefaultActionGroup(
      new AnAction(SSRBundle.message("save.template.text.button")) {

        @Override
        public void actionPerformed(AnActionEvent e) {
          ConfigurationManager.getInstance(getProject()).showSaveTemplateAsDialog(getConfiguration());
        }
      },
      new AnAction(SSRBundle.message("copy.existing.template.button")) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          SelectTemplateDialog dialog = new SelectTemplateDialog(searchContext.getProject(), false, isReplaceDialog());
          if (!dialog.showAndGet()) {
            return;
          }
          Configuration[] configurations = dialog.getSelectedConfigurations();
          if (configurations.length == 1) {
            setSearchPattern(configurations[0]);
          }
        }
      }
    );
    templateActionGroup.setPopup(true);
    templateActionGroup.getTemplatePresentation().setIcon(AllIcons.General.GearPlain);

    final AnAction filterAction = new AnAction(AllIcons.General.Filter) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        new EditVarConstraintsDialog(
          searchContext.getProject(),
          myConfiguration,
          getVariablesFromListeners(),
          fileTypes.getSelectedFileType()
        ).show();
        initiateValidation();
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myFilterEnabled);
      }
    };
    final DefaultActionGroup optionsActionGroup = new DefaultActionGroup(filterAction, templateActionGroup);
    ActionToolbarImpl optionsToolbar =
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
            .addComponent(recursiveMatching)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 15, 15)
            .addComponent(caseSensitiveMatch)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 15, 15)
            .addComponent(fileTypeLabel)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
            .addComponent(fileTypes)
            .addComponent(optionsToolbar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(historyToolbar)
            .addComponent(label)
            .addComponent(recursiveMatching)
            .addComponent(caseSensitiveMatch)
            .addComponent(fileTypeLabel)
            .addComponent(fileTypes)
            .addComponent(optionsToolbar)
    );

    detectFileTypeAndDialect();
    return northPanel;
  }

  @Nullable
  @Override
  protected JPanel createSouthAdditionalPanel() {
    if (!myRunFindActionOnClose) return null;
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
    openInNewTab = new JCheckBox(SSRBundle.message("open.in.new.tab.checkbox"));
    openInNewTab.setSelected(FindSettings.getInstance().isShowResultsInSeparateView());
    panel.add(openInNewTab, BorderLayout.EAST);
    return panel;
  }

  protected List<String> getVariablesFromListeners() {
    final Editor editor = searchCriteriaEdit.getEditor();
    if (editor == null) {
      return new SmartList<>();
    }
    return getVarsFrom(editor);
  }

  protected static List<String> getVarsFrom(Editor searchCriteriaEdit) {
    SubstitutionShortInfoHandler handler = SubstitutionShortInfoHandler.retrieve(searchCriteriaEdit);
    return (handler == null) ? new SmartList<>() : new ArrayList<>(handler.getVariables());
  }

  public final Project getProject() {
    return searchContext.getProject();
  }

  protected boolean isReplaceDialog() {
    return false;
  }

  @Override
  public void show() {
    StructuralSearchPlugin.getInstance(getProject()).setDialogVisible(true);

    if (!useLastConfiguration) {
      final Editor editor = searchContext.getEditor();
      boolean setSomeText = false;

      if (editor != null) {
        final SelectionModel selectionModel = editor.getSelectionModel();

        if (selectionModel.hasSelection()) {
          setText(selectionModel.getSelectedText());
          setSomeText = true;
        }
      }

      if (!setSomeText) {
        final Configuration configuration = ConfigurationManager.getInstance(getProject()).getMostRecentConfiguration();
        if (configuration != null) {
          setValuesFromConfig(configuration);
        }
      }
    }

    initiateValidation();

    super.show();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return searchCriteriaEdit;
  }

  @Override
  protected void doOKAction() {
    myDoingOkAction = true;
    final boolean result = isValid();
    myDoingOkAction = false;
    if (!result) return;

    myAlarm.cancelAllRequests();
    super.doOKAction();
    if (!myRunFindActionOnClose) return;

    final SearchScope selectedScope = myScopeChooserCombo.getSelectedScope();
    if (selectedScope == null) return;

    final FindSettings findSettings = FindSettings.getInstance();
    findSettings.setDefaultScopeName(selectedScope.getDisplayName());
    findSettings.setShowResultsInSeparateView(openInNewTab.isSelected());

    try {
      removeUnusedVariableConstraints(myConfiguration);
      ConfigurationManager.getInstance(getProject()).addHistoryConfiguration(myConfiguration);

      startSearching();
    }
    catch (MalformedPatternException ex) {
      reportMessage(SSRBundle.message("this.pattern.is.malformed.message", ex.getMessage()), searchCriteriaEdit);
    }
  }

  private void removeUnusedVariableConstraints(Configuration configuration) {
    final List<String> variableNames = getVariablesFromListeners();
    variableNames.add(Configuration.CONTEXT_VAR_NAME);
    configuration.getMatchOptions().retainVariableConstraints(variableNames);
  }

  public Configuration getConfiguration() {
    removeUnusedVariableConstraints(myConfiguration);
    setValuesToConfig(myConfiguration);
    return myConfiguration;
  }

  protected boolean isValid() {
    try {
      Matcher.validate(searchContext.getProject(), getConfiguration().getMatchOptions());
    }
    catch (MalformedPatternException ex) {
      reportMessage(SSRBundle.message("this.pattern.is.malformed.message",
                                      (ex.getMessage() != null) ? ex.getMessage() : ""), searchCriteriaEdit);
      return false;
    }
    catch (UnsupportedPatternException ex) {
      reportMessage(SSRBundle.message("this.pattern.is.unsupported.message", ex.getMessage()), searchCriteriaEdit);
      return false;
    }
    catch (NoMatchFoundException e) {
      reportMessage(e.getMessage(), searchCriteriaEdit);
      return false;
    }
    reportMessage(null, searchCriteriaEdit);
    return true;
  }

  protected void reportMessage(String message, EditorTextField editor) {
      com.intellij.util.ui.UIUtil.invokeLaterIfNeeded(() -> {
      editor.putClientProperty("JComponent.outline", message == null ? null : "error");
      editor.setToolTipText(message);
      editor.repaint();
      //status.setText(message);
      //status.setToolTipText(message);
      //status.revalidate();
      //statusText.setLabelFor(editor != null ? editor.getContentComponent() : null);
    });
  }

  protected void setValuesToConfig(Configuration config) {
    MatchOptions options = config.getMatchOptions();

    if (myShowScopePanel) {
      boolean searchWithinHierarchy = IdeBundle.message("scope.class.hierarchy").equals(myScopeChooserCombo.getSelectedScopeName());
      // We need to reset search within hierarchy scope during online validation since the scope works with user participation
      options.setScope(
        searchWithinHierarchy && !myDoingOkAction ? GlobalSearchScope.projectScope(getProject()) : myScopeChooserCombo.getSelectedScope());
    }
    options.setRecursiveSearch(isRecursiveSearchEnabled() && recursiveMatching.isSelected());

    final FileTypeInfo info = fileTypes.getSelectedItem();
    ourFtSearchVariant = info != null ? info.getFileType() : null;
    ourDialect = info != null ? info.getDialect() : null;
    ourContext = info != null ? info.getContext() : null;
    FileType fileType = ourFtSearchVariant;
    options.setFileType(fileType);
    options.setDialect(ourDialect);
    options.setPatternContext(ourContext);

    options.setSearchPattern(searchCriteriaEdit.getDocument().getText());
    options.setCaseSensitiveMatch(caseSensitiveMatch.isSelected());
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.ui.SearchDialog";
  }

  @Override
  public void dispose() {
    StructuralSearchPlugin.getInstance(getProject()).setDialogVisible(false);
    myAlarm.cancelAllRequests();
    searchCriteriaEdit.removeNotify();
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
