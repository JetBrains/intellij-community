/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.util.containers.ContainerUtil;
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

  public SelectTemplateDialog(Project project, boolean showHistory, boolean replace) {
    super(project, true);

    this.project = project;
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
      final TreePath selection = existingTemplatesComponent.getPatternTree().getSelectionPath();
      if (selection != null) {
        setPatternFromNode((DefaultMutableTreeNode)selection.getLastPathComponent());
      }
      else {
        showPatternPreviewFromConfiguration(null);
      }
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
    public void valueChanged(TreeSelectionEvent e) {
      if (e.getNewLeadSelectionPath() != null) {
        setPatternFromNode((DefaultMutableTreeNode)e.getNewLeadSelectionPath().getLastPathComponent());
      }
    }

    public void valueChanged(ListSelectionEvent e) {
      if (e.getValueIsAdjusting() || e.getLastIndex() == -1) return;
      int selectionIndex = existingTemplatesComponent.getHistoryList().getSelectedIndex();
      if (selectionIndex != -1) {
        setPatternFromList(selectionIndex);
      }
    }
  }

  private void setPatternFromList(int index) {
    showPatternPreviewFromConfiguration(
      (Configuration)existingTemplatesComponent.getHistoryList().getModel().getElementAt(index)
    );
  }

  protected JComponent createCenterPanel() {
    final JPanel centerPanel = new JPanel(new BorderLayout());
    Splitter splitter;

    centerPanel.add(BorderLayout.CENTER, splitter = new Splitter(false, 0.3f));
    centerPanel.add(splitter);

    splitter.setFirstComponent(
      showHistory ?
      existingTemplatesComponent.getHistoryPanel() :
      existingTemplatesComponent.getTemplatesPanel()
    );
    final JPanel panel;
    splitter.setSecondComponent(
      panel = new JPanel(new BorderLayout())
    );

    searchPatternEditor = UIUtil.createEditor(
      EditorFactory.getInstance().createDocument(""),
      project,
      false,
      true,
      ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), TemplateContextType.class)
    );

    JComponent centerComponent;

    if (replace) {
      replacePatternEditor = UIUtil.createEditor(
        EditorFactory.getInstance().createDocument(""),
        project,
        false,
        true,
        ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), TemplateContextType.class)
      );
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
    JPanel selectPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gb = new GridBagConstraints(0,0,0,0,0,0,GridBagConstraints.CENTER,GridBagConstraints.NONE, new Insets(0,0,0,0),0,0);
    selectPanel.add(new JLabel(SSRBundle.message("selecttemplate.template.label.please.select.template")), gb);
    myPreviewPanel.add(selectPanel, SELECT_TEMPLATE_CARD);

    panel.add(BorderLayout.CENTER, myPreviewPanel);

    final JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    labelPanel.add(new JLabel(SSRBundle.message("selecttemplate.template.preview")));
    labelPanel.add(UIUtil.createCompleteMatchInfo(() -> {
      final Configuration[] configurations = getSelectedConfigurations();
      return configurations.length != 1 ? null : configurations[0];
    }));
    panel.add(BorderLayout.NORTH, labelPanel);
    return centerPanel;
  }

  public void dispose() {
    EditorFactory.getInstance().releaseEditor(searchPatternEditor);
    if (replacePatternEditor != null) EditorFactory.getInstance().releaseEditor(replacePatternEditor);
    removeListeners();
    super.dispose();
  }

  public JComponent getPreferredFocusedComponent() {
    return showHistory ?
           existingTemplatesComponent.getHistoryList() :
           existingTemplatesComponent.getPatternTree();
  }

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

  private void setPatternFromNode(DefaultMutableTreeNode node) {
    if (node == null) return;
    final Object userObject = node.getUserObject();
    final Configuration configuration;

    // root could be without search template
    if (userObject instanceof Configuration) {
      configuration = (Configuration)userObject;
    }
    else {
      configuration = null;
    }

    showPatternPreviewFromConfiguration(configuration);
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

    searchPatternEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, configuration);

    if (replace) {
      String replacement = configuration instanceof ReplaceConfiguration
                    ? ((ReplaceConfiguration)configuration).getReplaceOptions().getReplacement()
                    : configuration.getMatchOptions().getSearchPattern();

      UIUtil.setContent(replacePatternEditor, replacement);
      replacePatternEditor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, configuration);
    }
  }

  @NotNull public Configuration[] getSelectedConfigurations() {
    if (showHistory) {
      final List<Configuration> selectedValues = existingTemplatesComponent.getHistoryList().getSelectedValuesList();
      return selectedValues.toArray(Configuration.EMPTY_ARRAY);
    }
    else {
      TreePath[] paths = existingTemplatesComponent.getPatternTree().getSelectionModel().getSelectionPaths();
      if (paths == null) {
        return new Configuration[0];
      }
      Collection<Configuration> configurations = new ArrayList<>();
      for (TreePath path : paths) {

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        final Object userObject = node.getUserObject();
        if (userObject instanceof Configuration) {
          configurations.add((Configuration)userObject);
        }
      }
      return configurations.toArray(new Configuration[configurations.size()]);
    }
  }

  public void selectConfiguration(String name) {
    existingTemplatesComponent.selectConfiguration(name);
  }
}
