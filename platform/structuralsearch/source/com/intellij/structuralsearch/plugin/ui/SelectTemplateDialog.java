// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.filters.ShortFilterTextProvider;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class SelectTemplateDialog extends DialogWrapper {
  private final ShortFilterTextProvider myShortFilterTextProvider;
  private final boolean showHistory;
  private Editor searchPatternEditor;
  private Editor replacePatternEditor;
  private final boolean replace;
  private final Project project;
  private final ExistingTemplatesComponent existingTemplatesComponent;

  private MySelectionListener selectionListener;
  private CardLayout myCardLayout;
  private JPanel myPreviewPanel;
  @NonNls private static final String PREVIEW_CARD = "Preview";
  @NonNls private static final String SELECT_TEMPLATE_CARD = "SelectCard";

  public SelectTemplateDialog(Project project, ShortFilterTextProvider provider, boolean showHistory, boolean replace) {
    super(project, true);

    this.project = project;
    myShortFilterTextProvider = provider;
    this.showHistory = showHistory;
    this.replace = replace;
    existingTemplatesComponent = ExistingTemplatesComponent.getInstance(this.project);

    setTitle(SSRBundle.message(this.showHistory ? "used.templates.history.dialog.title" : "existing.templates.dialog.title"));
    init();

    if (this.showHistory) {
      final int selection = existingTemplatesComponent.getHistoryList().getSelectedIndex();
      if (selection != -1) {
        setPatternFromList(selection);
      }
    }
    else {
      final Configuration configuration = existingTemplatesComponent.getSelectedConfiguration();
      showPatternPreviewFromConfiguration(configuration);
    }

    setupListeners();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    existingTemplatesComponent.finish(true);
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
    existingTemplatesComponent.finish(false);
  }

  class MySelectionListener implements TreeSelectionListener, ListSelectionListener {
    @Override
    public void valueChanged(TreeSelectionEvent e) {
      final Configuration configuration = existingTemplatesComponent.getSelectedConfiguration();
      showPatternPreviewFromConfiguration(configuration);
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (e.getValueIsAdjusting() || e.getLastIndex() == -1) return;
      int selectionIndex = existingTemplatesComponent.getHistoryList().getSelectedIndex();
      if (selectionIndex != -1) {
        setPatternFromList(selectionIndex);
      }
    }
  }

  private void setPatternFromList(int index) {
    showPatternPreviewFromConfiguration(existingTemplatesComponent.getHistoryList().getModel().getElementAt(index));
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel centerPanel = new JPanel(new BorderLayout());
    final Splitter splitter = new Splitter(false, 0.3f);

    centerPanel.add(BorderLayout.CENTER, splitter);
    centerPanel.add(splitter);

    splitter.setFirstComponent(showHistory ? existingTemplatesComponent.getHistoryPanel() : existingTemplatesComponent.getTemplatesPanel());
    final JPanel panel;
    splitter.setSecondComponent(panel = new JPanel(new BorderLayout()));

    searchPatternEditor = UIUtil.createEditor(EditorFactory.getInstance().createDocument(""), project, false, null);
    SubstitutionShortInfoHandler.install(searchPatternEditor, myShortFilterTextProvider, myDisposable);

    final JComponent centerComponent;
    if (replace) {
      replacePatternEditor = UIUtil.createEditor(EditorFactory.getInstance().createDocument(""), project, false, null);
      SubstitutionShortInfoHandler.install(replacePatternEditor, myShortFilterTextProvider, myDisposable);
      centerComponent = new Splitter(true);
      ((Splitter)centerComponent).setFirstComponent(searchPatternEditor.getComponent());
      ((Splitter)centerComponent).setSecondComponent(replacePatternEditor.getComponent());
    }
    else {
      centerComponent = searchPatternEditor.getComponent();
    }

    myCardLayout = new CardLayout();
    myPreviewPanel = new JPanel(myCardLayout);
    myPreviewPanel.add(centerComponent, PREVIEW_CARD);
    final JPanel selectPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 0, 0, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0);
    selectPanel.add(new JLabel(SSRBundle.message("selecttemplate.template.label.please.select.template")), gb);
    myPreviewPanel.add(selectPanel, SELECT_TEMPLATE_CARD);

    panel.add(BorderLayout.CENTER, myPreviewPanel);

    final JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    labelPanel.add(new JLabel(SSRBundle.message("selecttemplate.template.preview")));
    panel.add(BorderLayout.NORTH, labelPanel);
    return centerPanel;
  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(searchPatternEditor);
    if (replacePatternEditor != null) EditorFactory.getInstance().releaseEditor(replacePatternEditor);
    removeListeners();
    super.dispose();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return showHistory ?
           existingTemplatesComponent.getHistoryList() :
           existingTemplatesComponent.getPatternTree();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.ui.SelectTemplateDialog";
  }

  private void setupListeners() {
    existingTemplatesComponent.setOwner(this);
    selectionListener = new MySelectionListener();

    if (showHistory) {
      existingTemplatesComponent.getHistoryList().getSelectionModel().addListSelectionListener(selectionListener);
    }
    else {
      existingTemplatesComponent.getPatternTree().getSelectionModel().addTreeSelectionListener(selectionListener);
    }
  }

  private void removeListeners() {
    existingTemplatesComponent.setOwner(null);
    if (showHistory) {
      existingTemplatesComponent.getHistoryList().getSelectionModel().removeListSelectionListener(selectionListener);
    }
    else {
      existingTemplatesComponent.getPatternTree().getSelectionModel().removeTreeSelectionListener(selectionListener);
    }
  }

  private void showPatternPreviewFromConfiguration(@Nullable final Configuration configuration) {
    if (configuration == null) {
      myCardLayout.show(myPreviewPanel, SELECT_TEMPLATE_CARD);
      return;
    }
    else {
      myCardLayout.show(myPreviewPanel, PREVIEW_CARD);
    }
    final MatchOptions matchOptions = configuration.getMatchOptions();

    UIUtil.setContent(searchPatternEditor, matchOptions.getSearchPattern());
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(matchOptions.getFileType());
    if (profile != null) {
      TemplateEditorUtil.setHighlighter(searchPatternEditor, UIUtil.getTemplateContextType(profile));
    }
    searchPatternEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, configuration);
    SubstitutionShortInfoHandler.retrieve(searchPatternEditor).updateEditorInlays();

    if (replace) {
      final String replacement = configuration instanceof ReplaceConfiguration
                                 ? configuration.getReplaceOptions().getReplacement()
                                 : configuration.getMatchOptions().getSearchPattern();

      UIUtil.setContent(replacePatternEditor, replacement);
      if (profile != null) {
        TemplateEditorUtil.setHighlighter(replacePatternEditor, UIUtil.getTemplateContextType(profile));
      }
      replacePatternEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, configuration);
      SubstitutionShortInfoHandler.retrieve(replacePatternEditor).updateEditorInlays();
    }
  }

  public Configuration @NotNull [] getSelectedConfigurations() {
    if (showHistory) {
      final List<Configuration> selectedValues = existingTemplatesComponent.getHistoryList().getSelectedValuesList();
      return selectedValues.toArray(Configuration.EMPTY_ARRAY);
    }
    else {
      TreePath[] paths = existingTemplatesComponent.getPatternTree().getSelectionModel().getSelectionPaths();
      if (paths == null) {
        return Configuration.EMPTY_ARRAY;
      }
      Collection<Configuration> configurations = new ArrayList<>();
      for (TreePath path : paths) {

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        final Object userObject = node.getUserObject();
        if (userObject instanceof Configuration) {
          configurations.add((Configuration)userObject);
        }
      }
      return configurations.toArray(Configuration.EMPTY_ARRAY);
    }
  }
}
