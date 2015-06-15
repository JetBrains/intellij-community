/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.ui.*;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public abstract class NewEditChangelistPanel extends JPanel {
  private EditorTextField myNameTextField;
  private EditorTextField myDescriptionTextArea;
  private JPanel myAdditionalControlsPanel;
  private JCheckBox myMakeActiveCheckBox;

  private Consumer<LocalChangeList> myConsumer;
  private final Project myProject;

  public NewEditChangelistPanel(final Project project) {
    super(new GridBagLayout());
    myProject = project;
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                                   new Insets(1, 1, 1, 1), 0, 0);

    final JLabel nameLabel = new JLabel(VcsBundle.message("edit.changelist.name"));
    add(nameLabel, gb);
    ++ gb.gridx;
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.weightx = 1;
    myNameTextField = createEditorField(project, 1);
    myNameTextField.setOneLineMode(true);
    myNameTextField.setText("New changelist");
    myNameTextField.selectAll();
    add(myNameTextField, gb);
    nameLabel.setLabelFor(myNameTextField);

    ++ gb.gridy;
    gb.gridx = 0;

    gb.weightx = 0;
    gb.fill = GridBagConstraints.NONE;
    final JLabel commentLabel = new JLabel(VcsBundle.message("edit.changelist.description"));
    add(commentLabel, gb);
    ++ gb.gridx;
    gb.weightx = 1;
    gb.weighty = 1;
    gb.fill = GridBagConstraints.BOTH;
    myDescriptionTextArea = createEditorField(project, 4);
    myDescriptionTextArea.setOneLineMode(false);
    add(myDescriptionTextArea, gb);
    commentLabel.setLabelFor(myDescriptionTextArea);

    ++ gb.gridy;
    gb.gridx = 0;
    gb.gridwidth = 2;
    gb.weighty = 0;
    myAdditionalControlsPanel = new JPanel();
    final BoxLayout layout = new BoxLayout(myAdditionalControlsPanel, BoxLayout.X_AXIS);
    myAdditionalControlsPanel.setLayout(layout);
    myMakeActiveCheckBox = new JCheckBox(VcsBundle.message("new.changelist.make.active.checkbox"));
    myAdditionalControlsPanel.add(myMakeActiveCheckBox);
    add(myAdditionalControlsPanel, gb);
  }

  public JCheckBox getMakeActiveCheckBox() {
    return myMakeActiveCheckBox;
  }

  public void init(final LocalChangeList initial) {
    myMakeActiveCheckBox.setSelected(VcsConfiguration.getInstance(myProject).MAKE_NEW_CHANGELIST_ACTIVE);
    for (EditChangelistSupport support : Extensions.getExtensions(EditChangelistSupport.EP_NAME, myProject)) {
      support.installSearch(myNameTextField, myDescriptionTextArea);
      myConsumer = support.addControls(myAdditionalControlsPanel, initial);
    }
    myNameTextField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(DocumentEvent event) {
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        nameChangedImpl(myProject, initial);
      }
    });
    nameChangedImpl(myProject, initial);
  }

  protected void nameChangedImpl(final Project project, final LocalChangeList initial) {
    String name = getChangeListName();
    if (name == null || name.trim().length() == 0) {
      nameChanged("Cannot create new changelist with empty name.");
    } else if ((initial == null || !name.equals(initial.getName())) && ChangeListManager.getInstance(project).findChangeList(name) != null) {
      nameChanged(VcsBundle.message("new.changelist.duplicate.name.error"));
    } else {
      nameChanged(null);
    }
  }

  public void changelistCreatedOrChanged(LocalChangeList list) {
    if (myConsumer != null) {
      myConsumer.consume(list);
    }
  }

  public void setChangeListName(String s) {
    myNameTextField.setText(s);
  }

  public String getChangeListName() {
    return myNameTextField.getText();
  }

  public void setDescription(String s) {
    myDescriptionTextArea.setText(s);
  }

  public String getDescription() {
    return myDescriptionTextArea.getText();
  }

  public JComponent getContent() {
    return this;
  }

  public void requestFocus() {
    myNameTextField.requestFocus();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameTextField;
  }

  protected abstract void nameChanged(String errorMessage);

  private static EditorTextField createEditorField(final Project project, final int defaultLines) {
    final EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
    final EditorTextField editorField;

    final Set<EditorCustomization> editorFeatures = ContainerUtil.newHashSet();
    ContainerUtil.addIfNotNull(editorFeatures, SpellCheckingEditorCustomizationProvider.getInstance().getEnabledCustomization());

    if (defaultLines == 1) {
      editorFeatures.add(HorizontalScrollBarEditorCustomization.DISABLED);
      editorFeatures.add(OneLineEditorCustomization.ENABLED);
    } else {
      editorFeatures.add(SoftWrapsEditorCustomization.ENABLED);
    }
    editorField = service.getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, editorFeatures);
    final int height = editorField.getFontMetrics(editorField.getFont()).getHeight();
    editorField.getComponent().setMinimumSize(new Dimension(100, (int)(height * 1.3)));
    return editorField;
  }
}
