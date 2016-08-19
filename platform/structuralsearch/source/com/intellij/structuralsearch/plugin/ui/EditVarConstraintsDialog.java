package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.find.impl.RegExHelpPopup;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptLog;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.EditorTextField;
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
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Maxim.Mossienko
 * Date: Mar 25, 2004
 * Time: 1:52:18 PM
 */
class EditVarConstraintsDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.structuralsearch.plugin.ui.EditVarConstraintsDialog");

  private JTextField maxoccurs;
  private JCheckBox applyWithinTypeHierarchy;
  private JCheckBox notRegexp;
  private EditorTextField regexp;
  private JTextField minoccurs;
  private JPanel mainForm;
  private JCheckBox notWrite;
  private JCheckBox notRead;
  private JCheckBox write;
  private JCheckBox read;
  private JList parameterList;
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

  private ComboboxWithBrowseButton withinCombo;
  private JPanel containedInConstraints;
  private JCheckBox invertWithinIn;
  private JPanel expressionConstraints;
  private JPanel occurencePanel;
  private JPanel textConstraintsPanel;
  private JLabel myRegExHelpLabel;
  private JButton myZeroZeroButton;
  private JButton myOneOneButton;
  private JButton myZeroInfinityButton;
  private JButton myOneInfinityButton;
  private JButton myZeroOneButton;

  private static Project myProject;

  EditVarConstraintsDialog(final Project project, Configuration configuration, List<Variable> _variables, final FileType fileType) {
    super(project, false);

    variables = _variables;
    myConfiguration = configuration;

    setTitle(SSRBundle.message("editvarcontraints.edit.variables"));

    regexp.getDocument().addDocumentListener(new MyDocumentListener(notRegexp, wholeWordsOnly));
    regexp.getDocument().addDocumentListener(new DocumentAdapter() {
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
    read.addChangeListener(new MyChangeListener(notRead, false));
    write.addChangeListener(new MyChangeListener(notWrite, false));
    regexprForExprType.getDocument().addDocumentListener(new MyDocumentListener(exprTypeWithinHierarchy, notExprType));
    formalArgType.getDocument().addDocumentListener(new MyDocumentListener(formalArgTypeWithinHierarchy, invertFormalArgType));

    containedInConstraints.setVisible(false);
    withinCombo.getComboBox().setEditable(true);

    withinCombo.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(@NotNull final ActionEvent e) {
        final SelectTemplateDialog dialog = new SelectTemplateDialog(project, false, false);
        dialog.show();
        if (dialog.getExitCode() == OK_EXIT_CODE) {
          final Configuration[] selectedConfigurations = dialog.getSelectedConfigurations();
          if (selectedConfigurations.length == 1) {
            withinCombo.getComboBox().getEditor().setItem(selectedConfigurations[0].getMatchOptions().getSearchPattern()); // TODO:
          }
        }
      }
    });

    boolean hasContextVar = false;
    for(Variable var:variables) {
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

      read.setEnabled(true);
      notRead.setEnabled(false);
      write.setEnabled(true);
      notWrite.setEnabled(false);

      applyWithinTypeHierarchy.setEnabled(true);
    } else {
      formalArgTypeWithinHierarchy.setEnabled(false);
      invertFormalArgType.setEnabled(false);
      formalArgType.setEnabled(false);

      exprTypeWithinHierarchy.setEnabled(false);
      notExprType.setEnabled(false);
      regexprForExprType.setEnabled(false);

      read.setEnabled(false);
      notRead.setEnabled(false);
      write.setEnabled(false);
      notWrite.setEnabled(false);

      applyWithinTypeHierarchy.setEnabled(false);
    }

    parameterList.setModel(
      new AbstractListModel() {
        public Object getElementAt(int index) {
          return variables.get(index);
        }

        public int getSize() {
          return variables.size();
        }
      }
    );

    parameterList.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );

    parameterList.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        boolean rollingBackSelection;

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
      public void actionPerformed(@NotNull final ActionEvent e) {
        final List<String> variableNames = ContainerUtil.newArrayList(myConfiguration.getMatchOptions().getVariableConstraintNames());
        variableNames.add(ScriptLog.SCRIPT_LOG_VAR_NAME);
        variableNames.remove(CompiledPattern.ALL_CLASS_UNMATCHED_CONTENT_VAR_ARTIFICIAL_NAME);
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

  private static String stripReplacementVarDecoration(String name) {
    name = name.substring(0, name.length() - ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX.length());
    return name;
  }

  private static boolean isReplacementVariable(String name) {
    return name.endsWith(ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX);
  }

  private boolean validateParameters() {
    return validateRegExp(regexp) && validateRegExp(regexprForExprType) &&
           validateIntOccurence(minoccurs) &&
           validateScript(customScriptCode.getChildComponent()) &&
           (maxoccursUnlimited.isSelected() || validateIntOccurence(maxoccurs));
  }

  protected JComponent createCenterPanel() {
    return mainForm;
  }

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

    varInfo.setInvertReadAccess(notRead.isSelected());
    varInfo.setReadAccess(read.isSelected());
    varInfo.setInvertWriteAccess(notWrite.isSelected());
    varInfo.setWriteAccess(write.isSelected());
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

    final String withinConstraint = (String)withinCombo.getComboBox().getEditor().getItem();
    varInfo.setWithinConstraint(withinConstraint.length() > 0 ? "\"" + withinConstraint +"\"":"");
    varInfo.setInvertWithinConstraint(invertWithinIn.isSelected());
  }

  private static ReplacementVariableDefinition getOrAddReplacementVariableDefinition(String varName, Configuration configuration) {
    ReplaceOptions replaceOptions = ((ReplaceConfiguration)configuration).getOptions();
    String realVariableName = stripReplacementVarDecoration(varName);
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

  private void copyValuesToUI(Variable var) {
    final String varName = var.getName();

    if (isReplacementVariable(varName)) {
      final ReplacementVariableDefinition definition =
        ((ReplaceConfiguration)myConfiguration).getOptions().getVariableDefinition(stripReplacementVarDecoration(varName));

      restoreScriptCode(definition);
      setSearchConstraintsVisible(false);
      return;
    } else {
      setSearchConstraintsVisible(true);
    }

    final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    final MatchVariableConstraint varInfo = matchOptions.getVariableConstraint(varName);

    if (varInfo == null) {
      notRead.setSelected(false);
      notRegexp.setSelected(false);
      read.setSelected(false);
      notWrite.setSelected(false);
      write.setSelected(false);
      regexp.getDocument().setText("");

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

      withinCombo.getComboBox().getEditor().setItem("");
      invertWithinIn.setSelected(false);
    } else {
      notRead.setSelected(varInfo.isInvertReadAccess());
      read.setSelected(varInfo.isReadAccess());
      notWrite.setSelected(varInfo.isInvertWriteAccess());
      write.setSelected(varInfo.isWriteAccess());
      
      applyWithinTypeHierarchy.setSelected(varInfo.isWithinHierarchy());
      regexp.getDocument().setText(varInfo.getRegExp());
      //doProcessing(applyWithinTypeHierarchy,regexp);
      
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

      withinCombo.getComboBox().getEditor().setItem(StringUtil.stripQuotesAroundValue(varInfo.getWithinConstraint()));
      invertWithinIn.setSelected(varInfo.isInvertWithinConstraint());
    }

    final boolean contextVar = Configuration.CONTEXT_VAR_NAME.equals(var.getName());
    containedInConstraints.setVisible(contextVar);
    textConstraintsPanel.setVisible(!contextVar);
    partOfSearchResults.setEnabled(!contextVar);
    occurencePanel.setVisible(!contextVar);
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
      varInfo != null ? StringUtil.stripQuotesAroundValue(varInfo.getScriptCodeConstraint()) : "");
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.ui.EditVarConstraintsDialog";
  }

  private static boolean validateRegExp(EditorTextField field) {
    final String s = field.getDocument().getText();
    try {
      if (s.length() > 0) {
        Pattern.compile(s);
      }
    } catch(PatternSyntaxException ex) {
      Messages.showErrorDialog(SSRBundle.message("invalid.regular.expression", ex.getMessage()),
                               SSRBundle.message("invalid.regular.expression.title"));
      field.requestFocus();
      return false;
    }
    return true;
  }

  private static boolean validateScript(EditorTextField field) {
    final String text = field.getText();

    if (text.length() > 0) {
      final String message = ScriptSupport.checkValidScript(text);

      if (message != null) {
        Messages.showErrorDialog(message, SSRBundle.message("invalid.groovy.script"));
        field.requestFocus();
        return false;
      }
    }
    return true;
  }

  private static boolean validateIntOccurence(JTextField field) {
    try {
      if (Integer.parseInt(field.getText()) < 0) throw new NumberFormatException();
    } catch(NumberFormatException ex) {
      Messages.showErrorDialog(SSRBundle.message("invalid.occurence.count"), SSRBundle.message("invalid.occurence.count"));
      field.requestFocus();
      return false;
    }
    return true;
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

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
  }

  private static EditorTextField createRegexComponent() {
    @NonNls final String fileName = "1.regexp";
    final FileType fileType = getFileType(fileName);
    final Document doc = createDocument(fileName, fileType, "");
    return new EditorTextField(doc, myProject, fileType);
  }

  private static EditorTextField createScriptComponent() {
    @NonNls final String fileName = "1.groovy";
    final FileType fileType = getFileType(fileName);
    final Document doc = createDocument(fileName, fileType, "");
    return new EditorTextField(doc, myProject, fileType);
  }

  private static Document createDocument(final String fileName, final FileType fileType, String text) {
    final PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(fileName, fileType, text, -1, true);

    return PsiDocumentManager.getInstance(myProject).getDocument(file);
  }

  private static FileType getFileType(final String fileName) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    if (fileType == FileTypes.UNKNOWN) fileType = FileTypes.PLAIN_TEXT;
    return fileType;
  }

  public static void setProject(final Project project) {
    myProject = project;
  }

  private static class MyChangeListener implements ChangeListener {
    private final JComponent component;
    private final boolean inverted;

    MyChangeListener(JComponent _component, boolean _inverted) {
      component = _component;
      inverted = _inverted;
    }

    public void stateChanged(@NotNull ChangeEvent e) {
      final JCheckBox jCheckBox = (JCheckBox)e.getSource();
      component.setEnabled(inverted ^ jCheckBox.isSelected());
    }
  }

  private static class MyDocumentListener extends DocumentAdapter {
    private final JComponent[] components;

    private MyDocumentListener(JComponent... _components) {
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

  private static Editor createEditor(final Project project, final String text, final String fileName) {
    final FileType fileType = getFileType(fileName);
    final Document doc = createDocument(fileName, fileType, text);
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

  private static class EditScriptDialog extends DialogWrapper {
    private final Editor editor;
    private final String title;

    public EditScriptDialog(Project project, String text, Collection<String> names) {
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
}
