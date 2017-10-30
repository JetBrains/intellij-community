// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.ui.Splitter;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.UnsupportedPatternException;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.*;
import com.intellij.util.containers.hash.LinkedHashMap;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@SuppressWarnings({"RefusedBequest"})
public class ReplaceDialog extends SearchDialog {
  private Editor replaceCriteriaEdit;
  private JCheckBox shortenFQN;
  private JCheckBox formatAccordingToStyle;
  private JCheckBox useStaticImport;

  private String mySavedEditorText;

  @Override
  protected String getDefaultTitle() {
    return SSRBundle.message("structural.replace.title");
  }

  @Override
  protected JComponent createEditorContent() {
    JPanel result = new JPanel(new BorderLayout());
    Splitter p;

    result.add(BorderLayout.CENTER, p = new Splitter(true, 0.5f));
    p.setFirstComponent(super.createEditorContent());

    replaceCriteriaEdit = createEditor(searchContext, mySavedEditorText != null ? mySavedEditorText : "");
    JPanel replace = new JPanel(new BorderLayout());
    replace.add(BorderLayout.NORTH, new JLabel(SSRBundle.message("replacement.template.label")));
    replace.add(BorderLayout.CENTER, replaceCriteriaEdit.getComponent());
    replaceCriteriaEdit.getComponent().setMinimumSize(new Dimension(150, 100));

    p.setSecondComponent(replace);

    return result;
  }

  @Override
  protected int getRowsCount() {
    return super.getRowsCount() + 1;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.replace.ui.ReplaceDialog";
  }

  @Override
  protected void buildOptions(JPanel searchOptions) {
    super.buildOptions(searchOptions);
    searchOptions.add(UIUtil.createOptionLine(shortenFQN = new JCheckBox(
      SSRBundle.message("shorten.fully.qualified.names.checkbox"), true)));

    searchOptions.add(UIUtil.createOptionLine(formatAccordingToStyle = new JCheckBox(
      CodeInsightBundle.message("dialog.edit.template.checkbox.reformat.according.to.style"), true)));

    searchOptions.add(UIUtil.createOptionLine(useStaticImport = new JCheckBox(
      CodeInsightBundle.message("dialog.edit.template.checkbox.use.static.import"), true)));
  }

  public ReplaceDialog(SearchContext searchContext) {
    super(searchContext);
  }

  public ReplaceDialog(SearchContext searchContext, boolean showScope, boolean runFindActionOnClose) {
    super(searchContext, showScope, runFindActionOnClose);
  }

  @Override
  protected void startSearching() {
    new ReplaceCommand(myConfiguration, searchContext).startSearching();
  }

  @Override
  public Configuration createConfiguration(Configuration template) {
    return (template == null) ? new ReplaceConfiguration(USER_DEFINED, USER_DEFINED) : new ReplaceConfiguration(template);
  }

  @Override
  protected void disposeEditorContent() {
    mySavedEditorText = replaceCriteriaEdit.getDocument().getText();
    EditorFactory.getInstance().releaseEditor(replaceCriteriaEdit);
    super.disposeEditorContent();
  }

  @Override
  public void setValuesFromConfig(Configuration configuration) {
    super.setValuesFromConfig(configuration);
    final ReplaceConfiguration config = (ReplaceConfiguration)myConfiguration;
    final ReplaceOptions options = config.getReplaceOptions();

    UIUtil.setContent(replaceCriteriaEdit, config.getReplaceOptions().getReplacement());

    shortenFQN.setSelected(options.isToShortenFQN());
    formatAccordingToStyle.setSelected(options.isToReformatAccordingToStyle());
    useStaticImport.setSelected(options.isToUseStaticImport());
    replaceCriteriaEdit.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, myConfiguration);
  }

  @Override
  protected void setValuesToConfig(Configuration config) {
    super.setValuesToConfig(config);

    final ReplaceConfiguration replaceConfiguration = (ReplaceConfiguration)config;
    final ReplaceOptions options = replaceConfiguration.getReplaceOptions();

    options.setReplacement(replaceCriteriaEdit.getDocument().getText());
    options.setToShortenFQN(shortenFQN.isSelected());
    options.setToReformatAccordingToStyle(formatAccordingToStyle.isSelected());
    options.setToUseStaticImport(useStaticImport.isSelected());
  }

  @Override
  protected boolean isRecursiveSearchEnabled() {
    return false;
  }

  @Override
  protected List<Variable> getVariablesFromListeners() {
    List<Variable> vars = getVarsFrom(replaceCriteriaEdit);
    List<Variable> searchVars = super.getVariablesFromListeners();
    Map<String, Variable> varsMap = new LinkedHashMap<>(searchVars.size());

    for(Variable var:searchVars) varsMap.put(var.getName(), var);
    for(Variable var:vars) {
      if (!varsMap.containsKey(var.getName())) {
        String newVarName = var.getName() + ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX;
        varsMap.put(newVarName, new Variable(newVarName, null, null, false, false));
      }
    }
    return new ArrayList<>(varsMap.values());
  }

  @Override
  protected boolean isValid() {
    if (!super.isValid()) return false;

    try {
      Replacer.checkSupportedReplacementPattern(searchContext.getProject(), ((ReplaceConfiguration)myConfiguration).getReplaceOptions());
    }
    catch (UnsupportedPatternException ex) {
      reportMessage(SSRBundle.message("unsupported.replacement.pattern.message", ex.getMessage()), replaceCriteriaEdit);
      return false;
    }
    catch (MalformedPatternException ex) {
      reportMessage(SSRBundle.message("malformed.replacement.pattern.message", ex.getMessage()), replaceCriteriaEdit);
      return false;
    }

    return true;
  }

  @Override
  protected boolean isReplaceDialog() {
    return true;
  }

  @Override
  protected void setText(final String text) {
    super.setText(text);
    setTextForEditor(text, replaceCriteriaEdit);
  }
}
