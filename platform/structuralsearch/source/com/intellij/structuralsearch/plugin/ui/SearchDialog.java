// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.find.FindBundle;
import com.intellij.find.FindSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

/**
 *  Class to show the user the request for search
 */
public class SearchDialog extends DialogWrapper {
  protected SearchContext searchContext;

  // text for search
  protected Editor searchCriteriaEdit;

  // options of search scope
  private ScopeChooserCombo myScopeChooserCombo;

  private JCheckBox recursiveMatching;
  private JCheckBox caseSensitiveMatch;

  private JComboBox<FileType> fileTypes;
  private JComboBox<String> contexts;
  private JComboBox<Language> dialects;
  private JLabel status;
  private JLabel statusText;

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
  private JComponent myEditorPanel;

  public SearchDialog(SearchContext searchContext) {
    this(searchContext, true, true);
  }

  public SearchDialog(SearchContext searchContext, boolean showScope, boolean runFindActionOnClose) {
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
    myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myDisposable);
  }

  public void setUseLastConfiguration(boolean useLastConfiguration) {
    this.useLastConfiguration = useLastConfiguration;
  }

  void setSearchPattern(final Configuration config) {
    setValuesFromConfig(config);
    initiateValidation();
  }

  protected Editor createEditor(final SearchContext searchContext, String text) {
    Editor editor = null;

    if (fileTypes != null) {
      final FileType fileType = (FileType)fileTypes.getSelectedItem();
      final Language dialect = (Language)dialects.getSelectedItem();

      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);
      if (profile != null) {
        editor = profile.createEditor(searchContext, fileType, dialect, text, useLastConfiguration);
      }
    }

    if (editor == null) {
      final EditorFactory factory = EditorFactory.getInstance();
      final Document document = factory.createDocument("");
      editor = factory.createEditor(document, searchContext.getProject());
      editor.getSettings().setFoldingOutlineShown(false);
    }

    editor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(final DocumentEvent event) {
        initiateValidation();
      }
    });

    return editor;
  }

  void initiateValidation() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> {
      try {
        getOKAction().setEnabled(isValid());
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (RuntimeException e) {
        Logger.getInstance(SearchDialog.class).error(e);
      }
    }, 500);
  }

  protected void buildOptions(JPanel searchOptions) {
    recursiveMatching = new JCheckBox(SSRBundle.message("recursive.matching.checkbox"), true);
    if (isRecursiveSearchEnabled()) {
      searchOptions.add(UIUtil.createOptionLine(recursiveMatching));
    }

    caseSensitiveMatch = new JCheckBox(FindBundle.message("find.options.case.sensitive"), true);
    searchOptions.add(UIUtil.createOptionLine(caseSensitiveMatch));

    final List<FileType> types = new ArrayList<>();

    for (FileType fileType : StructuralSearchUtil.getSuitableFileTypes()) {
      if (StructuralSearchUtil.getProfileByFileType(fileType) != null) {
        types.add(fileType);
      }
    }
    Collections.sort(types, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

    final DefaultComboBoxModel<FileType> comboBoxModel = new DefaultComboBoxModel<>(types.toArray(new FileType[types.size()]));
    fileTypes = new ComboBox<>(comboBoxModel);
    fileTypes.setRenderer(new FileTypeRenderer());
    new ComboboxSpeedSearch(fileTypes) {
      @Override
      protected String getElementText(Object element) {
        return ((FileType)element).getName();
      }
    };
    contexts = new ComboBox<>(60);

    dialects = new ComboBox<>(120);
    dialects.setRenderer(new ListCellRendererWrapper<Language>() {
      @Override
      public void customize(JList list, Language value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          setText("None");
        }
        else {
          setText(value.getDisplayName());
        }
      }
    });
    dialects.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateEditor();
      }
    });
    new ComboboxSpeedSearch(dialects);

    final JLabel jLabel = new JLabel(SSRBundle.message("search.dialog.file.type.label"));
    final JLabel jLabel2 = new JLabel(SSRBundle.message("search.dialog.context.label"));
    final JLabel jLabel3 = new JLabel(SSRBundle.message("search.dialog.file.dialect.label"));
    searchOptions.add(
      UIUtil.createOptionLine(
        new JComponent[]{
          jLabel,
          fileTypes,
          (JComponent)Box.createHorizontalStrut(8),
          jLabel2,
          contexts,
          (JComponent)Box.createHorizontalStrut(8),
          jLabel3,
          dialects,
        }
      )
    );

    jLabel.setLabelFor(fileTypes);
    jLabel2.setLabelFor(contexts);
    jLabel3.setLabelFor(dialects);

    detectFileTypeAndDialect();

    fileTypes.setSelectedItem(ourFtSearchVariant);
    fileTypes.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateDialectsAndContexts();
          updateEditor();
          initiateValidation();
        }
      }
    });
    dialects.setSelectedItem(ourDialect);
    contexts.setSelectedItem(ourContext);

    updateDialectsAndContexts();
  }

  void updateEditor() {
    if (myContentPanel != null) {
      if (myEditorPanel != null) {
        myContentPanel.remove(myEditorPanel);
      }
      disposeEditorContent();
      myEditorPanel = createEditorContent();
      myContentPanel.add(myEditorPanel, BorderLayout.CENTER);
      myContentPanel.revalidate();
      searchCriteriaEdit.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
    }
  }

  void updateDialectsAndContexts() {
    final FileType fileType = (FileType)fileTypes.getSelectedItem();
    if (fileType instanceof LanguageFileType) {
      Language language = ((LanguageFileType)fileType).getLanguage();
      Language[] languageDialects = LanguageUtil.getLanguageDialects(language);
      Arrays.sort(languageDialects, Comparator.comparing(Language::getDisplayName));
      Language[] variants = new Language[languageDialects.length + 1];
      variants[0] = null;
      System.arraycopy(languageDialects, 0, variants, 1, languageDialects.length);
      dialects.setModel(new DefaultComboBoxModel<>(variants));
      dialects.setEnabled(variants.length > 1);
    }

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);

    if (profile instanceof StructuralSearchProfileBase) {
      final String[] contextNames = ((StructuralSearchProfileBase)profile).getContextNames();
      if (contextNames.length > 0) {
        contexts.setModel(new DefaultComboBoxModel<>(contextNames));
        contexts.setSelectedItem(contextNames[0]);
        contexts.setEnabled(true);
        return;
      }
    }
    contexts.setSelectedItem(null);
    contexts.setEnabled(false);
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
    setDialogTitle(myConfiguration);
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();

    UIUtil.setContent(searchCriteriaEdit, matchOptions.getSearchPattern());

    recursiveMatching.setSelected(isRecursiveSearchEnabled() && matchOptions.isRecursiveSearch());
    caseSensitiveMatch.setSelected(matchOptions.isCaseSensitiveMatch());

    fileTypes.setSelectedItem(matchOptions.getFileType());
    dialects.setSelectedItem(matchOptions.getDialect());
    if (matchOptions.getPatternContext() != null) {
      contexts.setSelectedItem(matchOptions.getPatternContext());
    }
    searchCriteriaEdit.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
  }

  void setDialogTitle(final Configuration configuration) {
    setTitle(getDefaultTitle() + " - " + configuration.getName());
  }

  public Configuration createConfiguration(Configuration template) {
    return (template == null) ? new SearchConfiguration(USER_DEFINED, USER_DEFINED) : new SearchConfiguration(template);
  }

  protected void setText(String text) {
    setTextForEditor(text, searchCriteriaEdit);
  }

  protected final void setTextForEditor(final String selection, Editor editor) {
    final Project project = searchContext.getProject();
    UIUtil.setContent(editor, selection);
    final Document document = editor.getDocument();
    editor.getSelectionModel().setSelection(0, document.getTextLength());
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    final PsiFile file = documentManager.getPsiFile(document);
    if (file == null) return;

    new WriteCommandAction(project, file) {
      @Override protected void run(@NotNull Result result) {
        CodeStyleManager.getInstance(project).adjustLineIndent(file, new TextRange(0, document.getTextLength()));
      }
    }.execute();
  }

  protected void startSearching() {
    new SearchCommand(myConfiguration, searchContext).startSearching();
  }

  protected String getDefaultTitle() {
    return SSRBundle.message("structural.search.title");
  }

  protected JComponent createEditorContent() {
    final JPanel result = new JPanel(new BorderLayout());

    searchCriteriaEdit = createEditor(searchContext, mySavedEditorText != null ? mySavedEditorText : "");
    result.add(BorderLayout.CENTER, searchCriteriaEdit.getComponent());
    result.setMinimumSize(new Dimension(150, 100));

    final JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    labelPanel.add(new JLabel(SSRBundle.message("search.template")));

    labelPanel.add(UIUtil.createCompleteMatchInfo(() -> myConfiguration));
    result.add(BorderLayout.NORTH, labelPanel);

    return result;
  }

  protected int getRowsCount() {
    return 4;
  }

  @Override
  protected JComponent createCenterPanel() {
    myContentPanel = new JPanel(new BorderLayout());
    myEditorPanel = createEditorContent();
    myContentPanel.add(BorderLayout.CENTER, myEditorPanel);
    myContentPanel.add(BorderLayout.SOUTH, Box.createVerticalStrut(8));
    JComponent centerPanel = new JPanel(new BorderLayout());
    {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(BorderLayout.CENTER, myContentPanel);
      panel.add(BorderLayout.SOUTH, createTemplateManagementButtons());
      centerPanel.add(BorderLayout.CENTER, panel);
    }

    JPanel optionsContent = new JPanel(new BorderLayout());
    centerPanel.add(BorderLayout.SOUTH, optionsContent);

    JPanel searchOptions = new JPanel();
    searchOptions.setLayout(new GridLayout(getRowsCount(), 1, 0, 0));
    searchOptions.setBorder(IdeBorderFactory.createTitledBorder(SSRBundle.message("ssdialog.options.group.border"),
                                                                true));

    JPanel allOptions = new JPanel(new BorderLayout());
    if (myShowScopePanel) {
      myScopeChooserCombo = new ScopeChooserCombo(
        searchContext.getProject(),
        true,
        false,
        FindSettings.getInstance().getDefaultScopeName()
      );
      Disposer.register(myDisposable, myScopeChooserCombo);
      JPanel scopePanel = new JPanel(new GridBagLayout());

      TitledSeparator separator = new TitledSeparator(SSRBundle.message("search.dialog.scope.label"), myScopeChooserCombo.getComboBox());
      scopePanel.add(separator, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                       JBUI.insetsTop(5), 0, 0));

      scopePanel.add(myScopeChooserCombo, new GridBagConstraints(0, 1, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                                 JBUI.insetsLeft(10), 0, 0));

      allOptions.add(
        scopePanel,
        BorderLayout.SOUTH
      );

      myScopeChooserCombo.getComboBox().addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          initiateValidation();
        }
      });
    }
    else {
      myScopeChooserCombo = null;
    }


    buildOptions(searchOptions);

    allOptions.add(searchOptions, BorderLayout.CENTER);
    optionsContent.add(allOptions, BorderLayout.CENTER);

    if (myRunFindActionOnClose) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
      openInNewTab = new JCheckBox(FindBundle.message("find.open.in.new.tab.checkbox"));
      openInNewTab.setSelected(FindSettings.getInstance().isShowResultsInSeparateView());
      ToolWindow findWindow = ToolWindowManager.getInstance(searchContext.getProject()).getToolWindow(ToolWindowId.FIND);
      openInNewTab.setEnabled(findWindow != null && findWindow.isAvailable());
      panel.add(openInNewTab, BorderLayout.EAST);

      optionsContent.add(BorderLayout.SOUTH, panel);
    }

    updateEditor();
    return centerPanel;
  }


  @Override
  protected JComponent createSouthPanel() {
    final JPanel statusPanel = new JPanel(new BorderLayout(5, 0));
    statusPanel.add(super.createSouthPanel(), BorderLayout.NORTH);
    statusPanel.add(statusText = new JLabel(SSRBundle.message("status.message")), BorderLayout.WEST);
    statusPanel.add(status = new JLabel(), BorderLayout.CENTER);
    status.setMinimumSize(new Dimension(0, 0));
    return statusPanel;
  }

  private JPanel createTemplateManagementButtons() {
    JPanel panel = new JPanel(null);
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.add(Box.createHorizontalGlue());

    panel.add(
      createJButtonForAction(new AbstractAction() {
        {
          putValue(NAME, SSRBundle.message("save.template.text.button"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
          final Configuration configuration = getConfiguration();
          if (!ConfigurationManager.getInstance(getProject()).showSaveTemplateAsDialog(configuration)) {
            return;
          }
          setDialogTitle(configuration);
        }
      })
    );

    panel.add(Box.createHorizontalStrut(8));

    panel.add(
      createJButtonForAction(
        new AbstractAction() {
          {
            putValue(NAME, SSRBundle.message("edit.variables.button"));
          }

          @Override
          public void actionPerformed(ActionEvent e) {
            new EditVarConstraintsDialog(
              searchContext.getProject(),
              myConfiguration,
              getVariablesFromListeners(),
              (FileType)fileTypes.getSelectedItem()
            ).show();
            initiateValidation();
          }
        }
      )
    );

    panel.add(Box.createHorizontalStrut(8));

    panel.add(
      createJButtonForAction(
        new AbstractAction() {
          {
            putValue(NAME, SSRBundle.message("history.button"));
          }

          @Override
          public void actionPerformed(ActionEvent e) {
            SelectTemplateDialog dialog = new SelectTemplateDialog(searchContext.getProject(), true, isReplaceDialog());
            if (!dialog.showAndGet()) {
              return;
            }
            Configuration[] configurations = dialog.getSelectedConfigurations();
            if (configurations.length == 1) {
              setSearchPattern(configurations[0]);
            }
          }
        }
      )
    );

    panel.add(Box.createHorizontalStrut(8));

    panel.add(
      createJButtonForAction(
        new AbstractAction() {
          {
            putValue(NAME, SSRBundle.message("copy.existing.template.button"));
          }

          @Override
          public void actionPerformed(ActionEvent e) {
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
      )
    );

    return panel;
  }

  protected List<Variable> getVariablesFromListeners() {
    return getVarsFrom(searchCriteriaEdit);
  }

  protected static List<Variable> getVarsFrom(Editor searchCriteriaEdit) {
    SubstitutionShortInfoHandler handler = SubstitutionShortInfoHandler.retrieve(searchCriteriaEdit);
    return (handler == null) ? new ArrayList<>() : new ArrayList<>(handler.getVariables());
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
    return searchCriteriaEdit.getContentComponent();
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
      filterOutUnusedVariableConstraints(myConfiguration);
      ConfigurationManager.getInstance(getProject()).addHistoryConfiguration(myConfiguration);

      startSearching();
    }
    catch (MalformedPatternException ex) {
      reportMessage(SSRBundle.message("this.pattern.is.malformed.message", ex.getMessage()), searchCriteriaEdit);
    }
  }

  private void filterOutUnusedVariableConstraints(Configuration configuration) {
    final List<Variable> variables = getVariablesFromListeners();
    final List<String> variableNames = new ArrayList<>();
    for (Variable variable : variables) {
      variableNames.add(variable.getName());
    }
    variableNames.add(Configuration.CONTEXT_VAR_NAME);
    configuration.getMatchOptions().retainVariableConstraints(variableNames);
  }

  public Configuration getConfiguration() {
    filterOutUnusedVariableConstraints(myConfiguration);
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
    reportMessage("", null);
    return true;
  }

  protected void reportMessage(String message, Editor editor) {
    com.intellij.util.ui.UIUtil.invokeLaterIfNeeded(() -> {
      status.setText(message);
      status.setToolTipText(message);
      status.revalidate();
      statusText.setLabelFor(editor != null ? editor.getContentComponent() : null);
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

    ourFtSearchVariant = (FileType)fileTypes.getSelectedItem();
    ourDialect = (Language)dialects.getSelectedItem();
    ourContext = (String)contexts.getSelectedItem();
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
    disposeEditorContent();

    myAlarm.cancelAllRequests();

    super.dispose();
    StructuralSearchPlugin.getInstance(getProject()).setDialogVisible(false);
  }

  protected void disposeEditorContent() {
    mySavedEditorText = searchCriteriaEdit.getDocument().getText();

    // this will remove from myExcludedSet
    final PsiFile file = PsiDocumentManager.getInstance(searchContext.getProject()).getPsiFile(searchCriteriaEdit.getDocument());
    if (file != null) {
      DaemonCodeAnalyzer.getInstance(searchContext.getProject()).setHighlightingEnabled(file, true);
    }

    EditorFactory.getInstance().releaseEditor(searchCriteriaEdit);
  }

  @Override
  protected String getHelpId() {
    return "find.structuredSearch";
  }

  public SearchContext getSearchContext() {
    return searchContext;
  }
}
