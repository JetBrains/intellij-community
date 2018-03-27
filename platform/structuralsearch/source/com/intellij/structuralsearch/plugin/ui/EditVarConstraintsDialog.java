// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.find.impl.RegExHelpPopup;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptLog;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.util.StructuralSearchScriptScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Maxim.Mossienko
 */
class EditVarConstraintsDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.structuralsearch.plugin.ui.EditVarConstraintsDialog");

  private JTextField maxoccurs;
  private JCheckBox applyWithinTypeHierarchy;
  private JCheckBox notRegexp;
  private EditorTextField regexp;
  private JTextField minoccurs;
  private JPanel mainForm;
  private JList<Variable> parameterList;
  private JCheckBox partOfSearchResults;
  private JCheckBox notExprType;
  private EditorTextField regexprForExprType;
  private final Configuration myConfiguration;
  private JCheckBox exprTypeWithinHierarchy;

  private final List<Variable> variables;
  private Variable current;
  private JCheckBox wholeWordsOnly;
  private JCheckBox formalArgTypeWithinHierarchy;
  private JCheckBox invertFormalArgType;
  private EditorTextField formalArgType;
  private ComponentWithBrowseButton<EditorTextField> customScriptCode;
  private JCheckBox maxoccursUnlimited;

  private TextFieldWithAutoCompletionWithBrowseButton withinTextField;
  private JPanel containedInConstraints;
  private JCheckBox invertWithin;
  private JPanel expressionConstraints;
  private JPanel occurencePanel;
  private JPanel textConstraintsPanel;
  private JLabel myRegExHelpLabel;
  private JButton myZeroZeroButton;
  private JButton myOneOneButton;
  private JButton myZeroInfinityButton;
  private JButton myOneInfinityButton;
  private JButton myZeroOneButton;
  private TextFieldWithAutoCompletionWithBrowseButton refererenceTargetTextField;
  private JPanel referenceTargetConstraints;
  private JBCheckBox invertReferenceTarget;

  private final Project myProject;

  EditVarConstraintsDialog(final Project project, Configuration configuration, List<Variable> _variables, final FileType fileType) {
    super(project, true);
    myProject = project;
    variables = _variables;
    myConfiguration = configuration;

    setTitle(SSRBundle.message("editvarcontraints.edit.variables"));

    regexp.getDocument().addDocumentListener(new MyDocumentListener(notRegexp, wholeWordsOnly));
    regexp.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        applyWithinTypeHierarchy.setEnabled(e.getDocument().getTextLength() > 0 && fileType == StdFileTypes.JAVA);
      }
    });
    myZeroZeroButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        minoccurs.setText("0");
        maxoccurs.setText("0");
        maxoccursUnlimited.setSelected(false);
      }
    });
    myZeroOneButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        minoccurs.setText("0");
        maxoccurs.setText("1");
        maxoccursUnlimited.setSelected(false);
      }
    });
    myOneOneButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        minoccurs.setText("1");
        maxoccurs.setText("1");
        maxoccursUnlimited.setSelected(false);
      }
    });
    myZeroInfinityButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        minoccurs.setText("0");
        maxoccursUnlimited.setSelected(true);
      }
    });
    myOneInfinityButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        minoccurs.setText("1");
        maxoccursUnlimited.setSelected(true);
      }
    });
    regexprForExprType.getDocument().addDocumentListener(new MyDocumentListener(exprTypeWithinHierarchy, notExprType));
    formalArgType.getDocument().addDocumentListener(new MyDocumentListener(formalArgTypeWithinHierarchy, invertFormalArgType));

    containedInConstraints.setVisible(false);

    final List<String> names = ConfigurationManager.getInstance(project).getAllConfigurationNames();
    withinTextField.setAutoCompletionItems(names);
    withinTextField.addActionListener(new SelectTemplateListener(project, withinTextField));
    refererenceTargetTextField.setAutoCompletionItems(names);
    refererenceTargetTextField.addActionListener(new SelectTemplateListener(project, refererenceTargetTextField));

    boolean hasContextVar = false;
    for (Variable var : variables) {
      if (Configuration.CONTEXT_VAR_NAME.equals(var.getName())) {
        hasContextVar = true; break;
      }
    }

    if (!hasContextVar) {
      variables.add(new Variable(Configuration.CONTEXT_VAR_NAME, "", "", true));
    }

    if (fileType == StdFileTypes.JAVA) {
      formalArgTypeWithinHierarchy.setEnabled(true);
      invertFormalArgType.setEnabled(true);
      formalArgType.setEnabled(true);

      exprTypeWithinHierarchy.setEnabled(true);
      notExprType.setEnabled(true);
      regexprForExprType.setEnabled(true);

      applyWithinTypeHierarchy.setEnabled(true);
    } else {
      formalArgTypeWithinHierarchy.setEnabled(false);
      invertFormalArgType.setEnabled(false);
      formalArgType.setEnabled(false);

      exprTypeWithinHierarchy.setEnabled(false);
      notExprType.setEnabled(false);
      regexprForExprType.setEnabled(false);

      applyWithinTypeHierarchy.setEnabled(false);
    }

    parameterList.setModel(
      new AbstractListModel<Variable>() {
        @Override
        public Variable getElementAt(int index) {
          return variables.get(index);
        }

        @Override
        public int getSize() {
          return variables.size();
        }
      }
    );

    parameterList.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );

    parameterList.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        boolean rollingBackSelection;

        @Override
        public void valueChanged(@NotNull ListSelectionEvent e) {
          if (e.getValueIsAdjusting()) return;
          if (rollingBackSelection) {
            rollingBackSelection=false;
            return;
          }
          final Variable var = variables.get(parameterList.getSelectedIndex());
          if (validateParameters()) {
            if (current!=null) copyValuesFromUI(current);
            ApplicationManager.getApplication().runWriteAction(() -> copyValuesToUI(var));
            current = var;
          } else {
            rollingBackSelection = true;
            parameterList.setSelectedIndex(e.getFirstIndex()==parameterList.getSelectedIndex()?e.getLastIndex():e.getFirstIndex());
          }
        }
      }
    );

    parameterList.setCellRenderer(
      new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(@NotNull JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          String name = ((Variable)value).getName();
          if (Configuration.CONTEXT_VAR_NAME.equals(name)) name = SSRBundle.message("complete.match.variable.name");
          if (isReplacementVariable(name)) {
            name = stripReplacementVarDecoration(name);
          }
          return super.getListCellRendererComponent(list, name, index, isSelected, cellHasFocus);
        }
      }
    );

    maxoccursUnlimited.addChangeListener(new MyChangeListener(maxoccurs, true));

    customScriptCode.getButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull final ActionEvent e) {
        final List<String> variableNames = ContainerUtil.newArrayList(myConfiguration.getMatchOptions().getVariableConstraintNames());
        variableNames.add(ScriptLog.SCRIPT_LOG_VAR_NAME);
        final EditScriptDialog dialog = new EditScriptDialog(project, customScriptCode.getChildComponent().getText(), variableNames);
        dialog.show();
        if (dialog.getExitCode() == OK_EXIT_CODE) {
          customScriptCode.getChildComponent().setText(dialog.getScriptText());
        }
      }
    });
    init();

    if (variables.size() > 0) parameterList.setSelectedIndex(0);
  }

  static String stripReplacementVarDecoration(String name) {
    name = name.substring(0, name.length() - ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX.length());
    return name;
  }

  static boolean isReplacementVariable(String name) {
    return name.endsWith(ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX);
  }

  boolean validateParameters() {
    return validateRegExp(regexp) && validateWithin() && validateCounts() && validateRegExp(regexprForExprType) &&
           validateRegExp(formalArgType) && validateScript();
  }

  @Override
  protected JComponent createCenterPanel() {
    return mainForm;
  }

  @Override
  protected void doOKAction() {
    if(validateParameters()) {
      if (current!=null) copyValuesFromUI(current);
      super.doOKAction();
    }
  }

  void copyValuesFromUI(Variable var) {
    final String varName = var.getName();

    if (isReplacementVariable(varName)) {
      saveScriptInfo(getOrAddReplacementVariableDefinition(varName, myConfiguration));
      return;
    }

    final MatchVariableConstraint varInfo = UIUtil.getOrAddVariableConstraint(varName, myConfiguration);

    varInfo.setRegExp(regexp.getDocument().getText());
    varInfo.setInvertRegExp(notRegexp.isSelected());

    final int minCount = Integer.parseInt(minoccurs.getText());
    varInfo.setMinCount(minCount);

    final int maxCount = maxoccursUnlimited.isSelected() ? Integer.MAX_VALUE : Integer.parseInt(maxoccurs.getText());

    varInfo.setMaxCount(maxCount);
    varInfo.setWithinHierarchy(applyWithinTypeHierarchy.isSelected());
    varInfo.setInvertRegExp(notRegexp.isSelected());

    final boolean target = partOfSearchResults.isSelected();
    if (target) {
      final MatchOptions matchOptions = myConfiguration.getMatchOptions();
      for (String name : matchOptions.getVariableConstraintNames()) {
        if (!name.equals(varName)) {
          matchOptions.getVariableConstraint(name).setPartOfSearchResults(false);
        }
      }
    }
    varInfo.setPartOfSearchResults(target);

    varInfo.setInvertExprType(notExprType.isSelected());
    varInfo.setNameOfExprType(regexprForExprType.getDocument().getText());
    varInfo.setExprTypeWithinHierarchy(exprTypeWithinHierarchy.isSelected());
    varInfo.setWholeWordsOnly(wholeWordsOnly.isSelected());
    varInfo.setInvertFormalType(invertFormalArgType.isSelected());
    varInfo.setFormalArgTypeWithinHierarchy(formalArgTypeWithinHierarchy.isSelected());
    varInfo.setNameOfFormalArgType(formalArgType.getDocument().getText());
    saveScriptInfo(varInfo);

    final String withinConstraint = withinTextField.getText().trim();
    final Configuration configuration = ConfigurationManager.getInstance(myProject).findConfigurationByName(withinConstraint);
    varInfo.setWithinConstraint(configuration != null || withinConstraint.isEmpty() ? withinConstraint : '"' + withinConstraint + '"');
    varInfo.setInvertWithinConstraint(invertWithin.isSelected());

    final String referenceTargetConstraint = refererenceTargetTextField.getText().trim();
    final Configuration configuration2 = ConfigurationManager.getInstance(myProject).findConfigurationByName(referenceTargetConstraint);
    varInfo.setReferenceConstraint((configuration2 != null || referenceTargetConstraint.isEmpty())
                                   ? referenceTargetConstraint
                                   : '"' + referenceTargetConstraint + '"');
    varInfo.setInvertReference(invertReferenceTarget.isSelected());

  }

  private static ReplacementVariableDefinition getOrAddReplacementVariableDefinition(String varName, Configuration configuration) {
    final ReplaceOptions replaceOptions = ((ReplaceConfiguration)configuration).getReplaceOptions();
    final String realVariableName = stripReplacementVarDecoration(varName);
    ReplacementVariableDefinition variableDefinition = replaceOptions.getVariableDefinition(realVariableName);

    if (variableDefinition == null) {
      variableDefinition = new ReplacementVariableDefinition();
      variableDefinition.setName(realVariableName);
      replaceOptions.addVariableDefinition(variableDefinition);
    }
    return variableDefinition;
  }

  private void saveScriptInfo(NamedScriptableDefinition varInfo) {
    varInfo.setScriptCodeConstraint("\"" + customScriptCode.getChildComponent().getText() + "\"");
  }

  void copyValuesToUI(Variable var) {
    final String varName = var.getName();

    if (isReplacementVariable(varName)) {
      final ReplacementVariableDefinition definition =
        ((ReplaceConfiguration)myConfiguration).getReplaceOptions().getVariableDefinition(stripReplacementVarDecoration(varName));

      restoreScriptCode(definition);
      setSearchConstraintsVisible(false);
      return;
    } else {
      setSearchConstraintsVisible(true);
    }

    final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    final MatchVariableConstraint varInfo = matchOptions.getVariableConstraint(varName);

    if (varInfo == null) {
      regexp.getDocument().setText("");
      notRegexp.setSelected(false);

      minoccurs.setText("1");
      maxoccurs.setText("1");
      maxoccursUnlimited.setSelected(false);
      applyWithinTypeHierarchy.setSelected(false);
      partOfSearchResults.setSelected(UIUtil.isTarget(varName, matchOptions));

      regexprForExprType.getDocument().setText("");
      notExprType.setSelected(false);
      exprTypeWithinHierarchy.setSelected(false);
      wholeWordsOnly.setSelected(false);

      invertFormalArgType.setSelected(false);
      formalArgTypeWithinHierarchy.setSelected(false);
      formalArgType.getDocument().setText("");
      customScriptCode.getChildComponent().setText("");

      withinTextField.setText("");
      invertWithin.setSelected(false);
      refererenceTargetTextField.setText("");
      invertReferenceTarget.setSelected(false);
    } else {
      applyWithinTypeHierarchy.setSelected(varInfo.isWithinHierarchy());
      regexp.getDocument().setText(varInfo.getRegExp());

      notRegexp.setSelected(varInfo.isInvertRegExp());
      minoccurs.setText(Integer.toString(varInfo.getMinCount()));

      if(varInfo.getMaxCount() == Integer.MAX_VALUE) {
        maxoccursUnlimited.setSelected(true);
        maxoccurs.setText("");
      } else {
        maxoccursUnlimited.setSelected(false);
        maxoccurs.setText(Integer.toString(varInfo.getMaxCount()));
      }

      partOfSearchResults.setSelected(UIUtil.isTarget(varName, matchOptions));

      exprTypeWithinHierarchy.setSelected(varInfo.isExprTypeWithinHierarchy());
      regexprForExprType.getDocument().setText(varInfo.getNameOfExprType());

      notExprType.setSelected( varInfo.isInvertExprType() );
      wholeWordsOnly.setSelected( varInfo.isWholeWordsOnly() );

      invertFormalArgType.setSelected( varInfo.isInvertFormalType() );
      formalArgTypeWithinHierarchy.setSelected(varInfo.isFormalArgTypeWithinHierarchy());
      formalArgType.getDocument().setText(varInfo.getNameOfFormalArgType());
      restoreScriptCode(varInfo);

      withinTextField.setText(StringUtil.unquoteString(varInfo.getWithinConstraint()));
      invertWithin.setSelected(varInfo.isInvertWithinConstraint());
      refererenceTargetTextField.setText(StringUtil.unquoteString(varInfo.getReferenceConstraint()));
      invertReferenceTarget.setSelected(varInfo.isInvertReference());
    }

    final boolean contextVar = Configuration.CONTEXT_VAR_NAME.equals(var.getName());
    containedInConstraints.setVisible(contextVar);
    textConstraintsPanel.setVisible(!contextVar);
    partOfSearchResults.setEnabled(!contextVar);
    occurencePanel.setVisible(!contextVar);
    referenceTargetConstraints.setVisible(!contextVar);
  }

  private void setSearchConstraintsVisible(boolean b) {
    textConstraintsPanel.setVisible(b);
    occurencePanel.setVisible(b);
    expressionConstraints.setVisible(b);
    partOfSearchResults.setVisible(b);
    containedInConstraints.setVisible(b);
  }

  private void restoreScriptCode(NamedScriptableDefinition varInfo) {
    customScriptCode.getChildComponent().setText(
      varInfo != null ? StringUtil.unquoteString(varInfo.getScriptCodeConstraint()) : "");
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.ui.EditVarConstraintsDialog";
  }

  private boolean validateRegExp(EditorTextField field) {
    try {
      //noinspection ResultOfMethodCallIgnored
      Pattern.compile(field.getText());
    } catch (PatternSyntaxException e) {
      return showError(field, e.getDescription());
    }
    return true;
  }

  private boolean validateScript() {
    final EditorTextField field = customScriptCode.getChildComponent();
    return showError(field, ScriptSupport.checkValidScript(field.getText()));
  }

  private boolean validateWithin() {
    final String within = withinTextField.getText();
    if (StringUtil.isEmpty(within)) {
      return true;
    }
    final ConfigurationManager configurationManager = ConfigurationManager.getInstance(myProject);
    final Set<String> seen = new HashSet<>();
    Configuration configuration = configurationManager.findConfigurationByName(within);
    while (configuration != null) {
      if (!seen.add(within)) {
        return showError(withinTextField.getChildComponent(), "Pattern recursively contained within itself");
      }
      final MatchVariableConstraint constraint = configuration.getMatchOptions().getVariableConstraint(Configuration.CONTEXT_VAR_NAME);
      if (constraint == null) {
        break;
      }
      configuration = configurationManager.findConfigurationByName(constraint.getWithinConstraint());
    }
    return true;
  }

  private boolean validateCounts() {
    final int minValue;
    try {
      minValue = Integer.parseInt(minoccurs.getText());
      if (minValue < 0) throw new NumberFormatException();
    }
    catch (NumberFormatException e) {
      return showError(minoccurs, SSRBundle.message("invalid.occurence.count"));
    }
    if (!maxoccursUnlimited.isSelected()) {
      try {
        if (Integer.parseInt(maxoccurs.getText()) < minValue) throw new NumberFormatException();
      }
      catch (NumberFormatException e) {
        return showError(maxoccurs, SSRBundle.message("invalid.occurence.count"));
      }
    }
    return true;
  }

  private boolean showError(JComponent component, String message) {
    if (message == null) return true;
    final Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, AllIcons.General.BalloonError,
                                                                                      MessageType.ERROR.getPopupBackground(), null).createBalloon();
    balloon.show(new RelativePoint(component, new Point(component.getWidth() / 2, component.getHeight())), Balloon.Position.below);
    Disposer.register(myDisposable, balloon);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(component, true));
    return false;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.dialogs.search.replace.structural.editvariable");
  }

  private void createUIComponents() {
    regexp = createRegexComponent();
    regexprForExprType = createRegexComponent();
    formalArgType = createRegexComponent();
    customScriptCode = new ComponentWithBrowseButton<>(createScriptComponent(), null);

    myRegExHelpLabel = RegExHelpPopup.createRegExLink(SSRBundle.message("regular.expression.help.label"), regexp, LOG);
    myRegExHelpLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
    withinTextField = new TextFieldWithAutoCompletionWithBrowseButton(myProject);
    refererenceTargetTextField = new TextFieldWithAutoCompletionWithBrowseButton(myProject);
  }

  private EditorTextField createRegexComponent() {
    @NonNls final String fileName = "1.regexp";
    final FileType fileType = getFileType(fileName);
    final Document doc = createDocument(fileName, fileType, "");
    return new EditorTextField(doc, myProject, fileType);
  }

  private EditorTextField createScriptComponent() {
    @NonNls final String fileName = "1.groovy";
    final FileType fileType = getFileType(fileName);
    final Document doc = createDocument(fileName, fileType, "");
    return new EditorTextField(doc, myProject, fileType);
  }

  private Document createDocument(final String fileName, final FileType fileType, String text) {
    final PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(fileName, fileType, text, -1, true);

    return PsiDocumentManager.getInstance(myProject).getDocument(file);
  }

  private static FileType getFileType(final String fileName) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    if (fileType == FileTypes.UNKNOWN) fileType = FileTypes.PLAIN_TEXT;
    return fileType;
  }

  private static class MyChangeListener implements ChangeListener {
    private final JComponent component;
    private final boolean inverted;

    MyChangeListener(JComponent _component, boolean _inverted) {
      component = _component;
      inverted = _inverted;
    }

    @Override
    public void stateChanged(@NotNull ChangeEvent e) {
      final JCheckBox jCheckBox = (JCheckBox)e.getSource();
      component.setEnabled(inverted ^ jCheckBox.isSelected());
    }
  }

  private static class MyDocumentListener implements DocumentListener {
    private final JComponent[] components;

    MyDocumentListener(JComponent... _components) {
      components = _components;
    }

    @Override
    public void documentChanged(DocumentEvent e) {
      final boolean enable = e.getDocument().getTextLength() > 0;
      for (JComponent component : components) {
        component.setEnabled(enable);
      }
    }
  }

  Editor createEditor(final Project project, final String text, final String fileName) {
    Language groovy = Language.findLanguageByID("Groovy");
    Document doc = null;
    final FileType fileType = getFileType(fileName);
    if (groovy != null) {
      // there is no right way to create a code fragment for generic language, so we use this hole since we need extend resolve scope
      for (StructuralSearchProfile profile : StructuralSearchProfile.EP_NAME.getExtensions()) {
        if (profile.isMyLanguage(groovy)) {
          PsiCodeFragment fragment = Objects.requireNonNull(profile.createCodeFragment(project, text, null));
          fragment.forceResolveScope(new StructuralSearchScriptScope(myProject));
          doc = PsiDocumentManager.getInstance(project).getDocument(fragment);
          break;
        }
      }
    }
    if (doc == null) {
      doc = createDocument(fileName, fileType, text);
    }
    final Editor editor = EditorFactory.getInstance().createEditor(doc, project);

    ((EditorEx)editor).setEmbeddedIntoDialogWrapper(true);
    final EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setRightMarginShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    ((EditorEx)editor).setHighlighter(HighlighterFactory.createHighlighter(fileType, DefaultColorSchemesManager.getInstance().getFirstScheme(), project));

    return editor;
  }

  private class EditScriptDialog extends DialogWrapper {
    private final Editor editor;
    private final String title;

    EditScriptDialog(Project project, String text, Collection<String> names) {
      super(project, true);
      setTitle(SSRBundle.message("edit.groovy.script.constraint.title"));
      editor = createEditor(project, text, "1.groovy");
      title = names.size() > 0 ? "Available variables: " + StringUtil.join(names, ", ") : "";
      init();
    }

    @Override
    protected String getDimensionServiceKey() {
      return getClass().getName();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return editor.getContentComponent();
    }

    @Override
    protected JComponent createCenterPanel() {
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(editor.getComponent(), BorderLayout.CENTER);
      if (!title.isEmpty()) {
        panel.add(new JLabel(title), BorderLayout.SOUTH);
      }
      return panel;
    }

    String getScriptText() {
      return editor.getDocument().getText();
    }

    @Override
    protected void dispose() {
      EditorFactory.getInstance().releaseEditor(editor);
      super.dispose();
    }
  }

  private static class SelectTemplateListener implements ActionListener {
    private final Project myProject;
    private final TextAccessor myTextField;

    public SelectTemplateListener(Project project, TextAccessor textField) {
      myProject = project;
      myTextField = textField;
    }

    @Override
    public void actionPerformed(@NotNull final ActionEvent e) {
      final SelectTemplateDialog dialog = new SelectTemplateDialog(myProject, false, false);
      dialog.selectConfiguration(myTextField.getText().trim());
      dialog.show();
      if (dialog.getExitCode() == OK_EXIT_CODE) {
        final Configuration[] selectedConfigurations = dialog.getSelectedConfigurations();
        if (selectedConfigurations.length == 1) {
          myTextField.setText(selectedConfigurations[0].getName());
        }
      }
    }
  }
}
