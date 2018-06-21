// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.find.FindBundle;
import com.intellij.find.FindInProjectSettings;
import com.intellij.find.FindSettings;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
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
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.IdeFocusManager;
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
import com.intellij.ui.EditorTextField;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.textCompletion.TextCompletionUtil;
import com.intellij.util.ui.LafIconLookup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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
  public static final String USER_DEFINED = SSRBundle.message("new.template.defaultname");

  // search info
  protected SearchContext mySearchContext;
  protected Configuration myConfiguration;
  @NonNls FileType myFileType = StructuralSearchUtil.getDefaultFileType();
  Language myDialect = null;
  String myContext = null;

  // ui management
  private final Alarm myAlarm;
  private boolean myUseLastConfiguration;
  private final boolean myShowScopePanel;
  private final boolean myRunFindActionOnClose;
  private boolean myDoingOkAction;
  private String mySavedEditorText;
  boolean myFilterButtonEnabled = false;

  // components
  private JCheckBox myRecursiveMatching;
  private JCheckBox myCaseSensitiveMatch;
  FileTypeSelector myFileTypesComboBox;
  protected EditorTextField mySearchCriteriaEdit;
  OnePixelSplitter myEditorPanel;
  FilterPanel myFilterPanel;
  private LinkComboBox myTargetComboBox;
  private ScopePanel myScopePanel;
  private JCheckBox myOpenInNewTab;

  public StructuralSearchDialog(SearchContext searchContext) {
    this(searchContext, true, true);
  }

  public StructuralSearchDialog(SearchContext searchContext, boolean showScope, boolean runFindActionOnClose) {
    super(searchContext.getProject(), true);

    if (showScope) setModal(false);
    myShowScopePanel = showScope;
    myRunFindActionOnClose = runFindActionOnClose;
    this.mySearchContext = searchContext;
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
    this.myUseLastConfiguration = useLastConfiguration;
  }

  void setSearchPattern(final Configuration config) {
    setValuesFromConfig(config);
    initiateValidation();
  }

  protected EditorTextField createEditor(final SearchContext searchContext, String text) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    assert profile != null;
    final Document document = profile.createDocument(searchContext.getProject(), myFileType, myDialect, text);
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(final DocumentEvent event) {
        initiateValidation();
      }
    });

    final EditorTextField textField = new EditorTextField(document, searchContext.getProject(), myFileType) {
      @Override
      protected EditorEx createEditor() {
        final EditorEx editorEx = super.createEditor();
        TextCompletionUtil.installCompletionHint(editorEx);
        editorEx.putUserData(STRUCTURAL_SEARCH, true);
        return editorEx;
      }
    };
    textField.setPreferredSize(new Dimension(850, 150));
    textField.setMinimumSize(new Dimension(200, 50));
    return textField;
  }

  void initiateValidation() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> {
      try {
        final boolean valid = isValid();
        final boolean compiled = isCompiled();
        ApplicationManager.getApplication().invokeLater(() -> {
          myFilterButtonEnabled = compiled;
          final List<String> variables = getSearchVariables();
          if (!variables.isEmpty()) {
            variables.add(SSRBundle.message("complete.match.variable.name"));
            myTargetComboBox.setItems(variables);
            myTargetComboBox.setEnabled(true);
            final MatchOptions options = myConfiguration.getMatchOptions();
            for (String variable : variables) {
              final MatchVariableConstraint constraint = options.getVariableConstraint(variable);
              if (constraint != null && constraint.isPartOfSearchResults()) {
                myTargetComboBox.setSelectedItem(variable);
                break;
              }
            }
          }
          else {
            myTargetComboBox.setEnabled(false);
          }
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
      final CompiledPattern compiledPattern = PatternCompiler.compilePattern(getProject(), myConfiguration.getMatchOptions(), false);
      myFilterPanel.setCompiledPattern(compiledPattern);
      return compiledPattern != null;
    } catch (MalformedPatternException e) {
      return false;
    }
  }

  void updateEditor() {
    if (myEditorPanel != null) {
      if (mySearchCriteriaEdit != null) {
        mySavedEditorText = mySearchCriteriaEdit.getText();
        myEditorPanel.remove(mySearchCriteriaEdit);
      }
      mySearchCriteriaEdit = createEditor(mySearchContext, mySavedEditorText != null ? mySavedEditorText : "");
      myEditorPanel.setFirstComponent(mySearchCriteriaEdit);
      myEditorPanel.revalidate();
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
      assert profile != null;

      mySearchCriteriaEdit.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          final Editor editor = mySearchCriteriaEdit.getEditor();
          if (editor == null) return;
          TemplateEditorUtil.setHighlighter(editor, profile.getTemplateContextType());
          SubstitutionShortInfoHandler.install(editor, variableName ->
            myFilterPanel.setFilters(myConfiguration.getMatchOptions().getVariableConstraint(variableName)));
          //myFilterPanel.setFilters(UIUtil.getOrAddVariableConstraint(Configuration.CONTEXT_VAR_NAME, myConfiguration));
          editor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
          final Project project = mySearchContext.getProject();
          final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(mySearchCriteriaEdit.getDocument());
          if (file != null) {
            DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file, false);
          }
          mySearchCriteriaEdit.removePropertyChangeListener(this);
        }
      });
    }
  }

  private void detectFileTypeAndDialect() {
    final PsiFile file = mySearchContext.getFile();
    if (file != null) {
      PsiElement context = null;

      if (mySearchContext.getEditor() != null) {
        context = file.findElementAt(mySearchContext.getEditor().getCaretModel().getOffset());
        if (context != null) {
          context = context.getParent();
        }
      }
      if (context == null) {
        context = file;
      }

      FileType detectedFileType = null;

      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(context);
      if (profile != null) {
        final FileType fileType = profile.detectFileType(context);
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

      myFileType = (detectedFileType != null) ? detectedFileType : StructuralSearchUtil.getDefaultFileType();
    }
  }

  protected boolean isRecursiveSearchEnabled() {
    return myShowScopePanel;
  }

  public void setValuesFromConfig(Configuration configuration) {
    myConfiguration = createConfiguration(configuration);
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    final List<String> names = new ArrayList<>(matchOptions.getVariableConstraintNames());
    names.remove(Configuration.CONTEXT_VAR_NAME);
    names.add(SSRBundle.message("complete.match.variable.name"));
    myTargetComboBox.setItems(names);
    for (String name : names) {
      final MatchVariableConstraint constraint = matchOptions.getVariableConstraint(name);
      if (constraint != null && constraint.isPartOfSearchResults()) {
        myTargetComboBox.setSelectedItem(name);
        break;
      }
    }
    myScopePanel.setScope(matchOptions.getScope());

    final Document document = mySearchCriteriaEdit.getDocument();
    CommandProcessor.getInstance().executeCommand(mySearchContext.getProject(), () -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        document.replaceString(0, document.getTextLength(), matchOptions.getSearchPattern());
      });
    }, null, null);

    myRecursiveMatching.setSelected(isRecursiveSearchEnabled() && matchOptions.isRecursiveSearch());
    myCaseSensitiveMatch.setSelected(matchOptions.isCaseSensitiveMatch());
    myFileTypesComboBox.setSelectedItem(matchOptions.getFileType(), matchOptions.getDialect(), matchOptions.getPatternContext());
    final Editor editor = mySearchCriteriaEdit.getEditor();
    if (editor != null) {
      editor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
    }
  }

  public Configuration createConfiguration(Configuration template) {
    return (template == null) ? new SearchConfiguration(USER_DEFINED, USER_DEFINED) : new SearchConfiguration(template);
  }

  protected void setText(String text) {
    setTextForEditor(text, mySearchCriteriaEdit);
  }

  protected final void setTextForEditor(final String selection, EditorTextField editor) {
    editor.setText(selection);
    editor.selectAll();
    final Project project = mySearchContext.getProject();
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
    new SearchCommand(myConfiguration, mySearchContext).startSearching();
  }

  protected String getDefaultTitle() {
    return SSRBundle.message("structural.search.title");
  }

  @Override
  protected JComponent createCenterPanel() {
    myEditorPanel = new OnePixelSplitter(false, 1.0f) {
      /*final Timer myTimer = com.intellij.util.ui.UIUtil.createNamedTimer("SlidingSplitter", 17);

      @Override
      public void setSecondComponent(@Nullable JComponent component) {
        if (component != null) {
          final Dimension size = component.getMinimumSize();
          component.setMinimumSize(new Dimension(0, size.height));
          final ActionListener listener = e -> {
            final int width = component.getMinimumSize().width;
            if (width < size.width) {
              component.setMinimumSize(new Dimension(width + 1, size.height));
              myEditorPanel.revalidate();
            }
            else {
              myTimer.stop();
            }
          };
          for (ActionListener actionListener: myTimer.getActionListeners()) {
            myTimer.removeActionListener(actionListener);
          }
          myTimer.addActionListener(listener);
          myTimer.start();
          super.setSecondComponent(component);
        }
        super.setSecondComponent(component);
      }*/
    };
    myEditorPanel.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE);
    myEditorPanel.getDivider().setOpaque(false);
    mySearchCriteriaEdit = createEditor(mySearchContext, mySavedEditorText != null ? mySavedEditorText : "");
    myEditorPanel.add(BorderLayout.CENTER, mySearchCriteriaEdit);

    myScopePanel = new ScopePanel(getProject());
    if (myShowScopePanel) {
      myScopePanel.setRecentDirectories(FindInProjectSettings.getInstance(getProject()).getRecentDirectories());
      myScopePanel.setScopeCallback(s -> initiateValidation());
    }
    else {
      myScopePanel.setEnabled(false);
    }

    myFilterPanel =
      new FilterPanel(getProject(), StructuralSearchUtil.getProfileByFileType(myFileType), getDisposable());
    myFilterPanel.getComponent().setMinimumSize(new Dimension(300, 50));

    final JLabel searchTargetLabel = new JLabel(SSRBundle.message("search.target.label"));
    myTargetComboBox = new LinkComboBox(SSRBundle.message("complete.match.variable.name"));

    final JPanel centerPanel = new JPanel(null);
    final GroupLayout layout = new GroupLayout(centerPanel);
    centerPanel.setLayout(layout);
    layout.setHorizontalGroup(
      layout.createParallelGroup()
            .addGroup(layout.createSequentialGroup()
                            .addComponent(myEditorPanel)
            )
            .addComponent(myScopePanel)
            .addGroup(layout.createSequentialGroup()
                            .addComponent(searchTargetLabel)
                            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
                            .addComponent(myTargetComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            )
    );
    layout.setVerticalGroup(
      layout.createSequentialGroup()
            .addGroup(layout.createParallelGroup()
                            .addComponent(myEditorPanel)
            )
            .addComponent(myScopePanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 2, 2)
            .addGroup(layout.createParallelGroup()
                            .addComponent(searchTargetLabel)
                            .addComponent(myTargetComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            )
    );

    updateEditor();
    return centerPanel;
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    final DefaultActionGroup historyActionGroup = new DefaultActionGroup(new AnAction(getShowHistoryIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final SelectTemplateDialog dialog = new SelectTemplateDialog(mySearchContext.getProject(), true, isReplaceDialog());
        if (!dialog.showAndGet()) {
          return;
        }
        final Configuration[] configurations = dialog.getSelectedConfigurations();
        if (configurations.length == 1) {
          setSearchPattern(configurations[0]);
          initiateValidation();
        }
      }
    });
    final ActionToolbarImpl historyToolbar =
      (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, historyActionGroup, true);
    historyToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    final JLabel label = new JLabel(SSRBundle.message("search.template"));
    UIUtil.installCompleteMatchInfo(label, () -> myConfiguration);

    myRecursiveMatching = new JCheckBox(SSRBundle.message("recursive.matching.checkbox"), true);
    myRecursiveMatching.setVisible(isRecursiveSearchEnabled());
    myCaseSensitiveMatch = new JCheckBox(FindBundle.message("find.popup.case.sensitive"), true);
    final List<FileType> types = new ArrayList<>();
    for (FileType fileType : StructuralSearchUtil.getSuitableFileTypes()) {
      if (StructuralSearchUtil.getProfileByFileType(fileType) != null) {
        types.add(fileType);
      }
    }
    Collections.sort(types, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
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
          updateEditor();
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
        public void actionPerformed(AnActionEvent e) {
          ConfigurationManager.getInstance(getProject()).showSaveTemplateAsDialog(getConfiguration());
        }
      },
      new AnAction(SSRBundle.message("copy.existing.template.button")) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final SelectTemplateDialog dialog = new SelectTemplateDialog(mySearchContext.getProject(), false, isReplaceDialog());
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
      public boolean isSelected(AnActionEvent e) {
        return myEditorPanel.getSecondComponent() != null;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myEditorPanel.setSecondComponent(state ? myFilterPanel.getComponent() : null);
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myFilterButtonEnabled);
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
            .addComponent(myRecursiveMatching)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 15, 15)
            .addComponent(myCaseSensitiveMatch)
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
            .addComponent(myRecursiveMatching)
            .addComponent(myCaseSensitiveMatch)
            .addComponent(fileTypeLabel)
            .addComponent(myFileTypesComboBox)
            .addComponent(optionsToolbar)
    );

    detectFileTypeAndDialect();
    return northPanel;
  }

  @Nullable
  @Override
  protected JPanel createSouthAdditionalPanel() {
    if (!myRunFindActionOnClose) return null;
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
    myOpenInNewTab = new JCheckBox(SSRBundle.message("open.in.new.tab.checkbox"));
    myOpenInNewTab.setSelected(FindSettings.getInstance().isShowResultsInSeparateView());
    panel.add(myOpenInNewTab, BorderLayout.EAST);
    return panel;
  }

  protected List<String> getVariablesFromListeners() {
    return getSearchVariables();
  }

  @NotNull
  private List<String> getSearchVariables() {
    final Editor editor = mySearchCriteriaEdit.getEditor();
    if (editor == null) {
      return new SmartList<>();
    }
    return getVarsFrom(editor);
  }

  protected static List<String> getVarsFrom(Editor searchCriteriaEdit) {
    final SubstitutionShortInfoHandler handler = SubstitutionShortInfoHandler.retrieve(searchCriteriaEdit);
    return (handler == null) ? new SmartList<>() : new ArrayList<>(handler.getVariables());
  }

  public final Project getProject() {
    return mySearchContext.getProject();
  }

  protected boolean isReplaceDialog() {
    return false;
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
    return mySearchCriteriaEdit;
  }

  @Override
  protected void doOKAction() {
    myDoingOkAction = true;
    final boolean result = isValid();
    myDoingOkAction = false;
    if (!result) return;

    myAlarm.cancelAllRequests();
    removeUnusedVariableConstraints(myConfiguration);
    final SearchScope scope = myScopePanel.getScope();
    if (scope == null) return;
    super.doOKAction();
    if (!myRunFindActionOnClose) return;

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
      reportMessage(SSRBundle.message("this.pattern.is.malformed.message", ex.getMessage()), mySearchCriteriaEdit);
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
      Matcher.validate(mySearchContext.getProject(), getConfiguration().getMatchOptions());
    }
    catch (MalformedPatternException ex) {
      reportMessage(SSRBundle.message("this.pattern.is.malformed.message",
                                      (ex.getMessage() != null) ? ex.getMessage() : ""), mySearchCriteriaEdit);
      return false;
    }
    catch (UnsupportedPatternException ex) {
      reportMessage(SSRBundle.message("this.pattern.is.unsupported.message", ex.getMessage()), mySearchCriteriaEdit);
      return false;
    }
    catch (NoMatchFoundException e) {
      reportMessage(e.getMessage(), mySearchCriteriaEdit);
      return false;
    }
    reportMessage(null, mySearchCriteriaEdit);
    return myScopePanel.getScope() != null;
  }

  protected void reportMessage(String message, JComponent component) {
    com.intellij.util.ui.UIUtil.invokeLaterIfNeeded(() -> {
      component.putClientProperty("JComponent.outline", message == null ? null : "error");
      component.repaint();

      if (message == null) return;
      final Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, MessageType.ERROR, null).createBalloon();
      if (component == mySearchCriteriaEdit) {
        balloon.show(new RelativePoint(component, new Point(component.getWidth() / 2, component.getHeight())), Balloon.Position.below);
      }
      else {
        balloon.show(new RelativePoint(component, new Point(component.getWidth() / 2, 0)), Balloon.Position.above);
      }
      //balloon.show(new RelativePoint(component, new Point(component.getWidth() / 2, 0)), Balloon.Position.above);
      balloon.showInCenterOf(component);
      Disposer.register(myDisposable, balloon);
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(component, true));
    });
  }

  protected void setValuesToConfig(Configuration config) {
    final MatchOptions options = config.getMatchOptions();

    if (myShowScopePanel) {
      final SearchScope scope = myScopePanel.getScope();
      if (scope != null) {
        final boolean searchWithinHierarchy = IdeBundle.message("scope.class.hierarchy").equals(scope.getDisplayName());
        // We need to reset search within hierarchy scope during online validation since the scope works with user participation
        options.setScope(searchWithinHierarchy && !myDoingOkAction ? GlobalSearchScope.projectScope(getProject()) : scope);
      }
    }
    options.setRecursiveSearch(isRecursiveSearchEnabled() && myRecursiveMatching.isSelected());
    options.setFileType(myFileType);
    options.setDialect(myDialect);
    options.setPatternContext(myContext);
    options.setSearchPattern(mySearchCriteriaEdit.getDocument().getText());
    options.setCaseSensitiveMatch(myCaseSensitiveMatch.isSelected());
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog";
  }

  @Override
  public void dispose() {
    StructuralSearchPlugin.getInstance(getProject()).setDialogVisible(false);
    myAlarm.cancelAllRequests();
    mySearchCriteriaEdit.removeNotify();
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
