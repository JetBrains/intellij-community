// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public abstract class NewEditChangelistPanel extends JPanel {
  protected final EditorTextField myNameTextField;
  protected final EditorTextField myDescriptionTextArea;
  private final JPanel myAdditionalControlsPanel;
  private final JCheckBox myMakeActiveCheckBox;

  private Consumer<LocalChangeList> myConsumer;
  protected final Project myProject;

  public NewEditChangelistPanel(final Project project) {
    super(new GridBagLayout());
    myProject = project;
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                         JBUI.insets(1), 0, 0);

    final JLabel nameLabel = new JLabel(VcsBundle.message("edit.changelist.name"));
    add(nameLabel, gb);
    ++gb.gridx;
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.weightx = 1;
    ComponentWithTextFieldWrapper componentWithTextField = createComponentWithTextField(project);
    myNameTextField = componentWithTextField.getEditorTextField();
    myNameTextField.setOneLineMode(true);
    String generateUniqueName = ChangeListUtil.createNameForChangeList(project, VcsBundle.message("changes.new.changelist"));
    myNameTextField.setText(generateUniqueName);
    myNameTextField.selectAll();
    add(componentWithTextField.myComponent, gb);
    nameLabel.setLabelFor(myNameTextField);

    ++ gb.gridy;
    gb.gridx = 0;

    gb.weightx = 0;
    gb.fill = GridBagConstraints.NONE;
    gb.anchor = GridBagConstraints.NORTHWEST;
    final JLabel commentLabel = new JLabel(VcsBundle.message("edit.changelist.description"));
    UIUtil.addInsets(commentLabel, JBUI.insetsRight(4));
    add(commentLabel, gb);
    ++ gb.gridx;
    gb.weightx = 1;
    gb.weighty = 1;
    gb.fill = GridBagConstraints.BOTH;
    gb.insets = JBUI.insetsTop(2);
    myDescriptionTextArea = createEditorField(project, 4);
    myDescriptionTextArea.setOneLineMode(false);
    add(myDescriptionTextArea, gb);
    commentLabel.setLabelFor(myDescriptionTextArea);
    gb.insets = JBUI.insetsTop(0);

    ++ gb.gridy;
    gb.gridx = 0;
    gb.gridwidth = 2;
    gb.weighty = 0;
    myAdditionalControlsPanel = new JPanel();
    final BoxLayout layout = new BoxLayout(myAdditionalControlsPanel, BoxLayout.X_AXIS);
    myAdditionalControlsPanel.setLayout(layout);
    myMakeActiveCheckBox = new JCheckBox(VcsBundle.message("new.changelist.make.active.checkbox"));
    myMakeActiveCheckBox.setBorder(JBUI.Borders.emptyRight(4));
    myAdditionalControlsPanel.add(myMakeActiveCheckBox);
    add(myAdditionalControlsPanel, gb);
  }

  public JCheckBox getMakeActiveCheckBox() {
    return myMakeActiveCheckBox;
  }

  public void init(@Nullable LocalChangeList initial) {
    myMakeActiveCheckBox.setSelected(VcsConfiguration.getInstance(myProject).MAKE_NEW_CHANGELIST_ACTIVE);
    for (EditChangelistSupport support : EditChangelistSupport.EP_NAME.getExtensions(myProject)) {
      support.installSearch(myNameTextField, myDescriptionTextArea);
      myConsumer = support.addControls(myAdditionalControlsPanel, initial);
    }
    myNameTextField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        nameChangedImpl(myProject, initial);
      }
    });
    nameChangedImpl(myProject, initial);
  }

  protected void nameChangedImpl(final Project project, final LocalChangeList initial) {
    String name = getChangeListName();
    if (name == null || name.trim().length() == 0) {
      nameChanged(VcsBundle.message("new.changelist.empty.name.error"));
    } else if ((initial == null || !name.equals(initial.getName())) && ChangeListManager.getInstance(project).findChangeList(name) != null) {
      nameChanged(VcsBundle.message("new.changelist.duplicate.name.error"));
    } else {
      nameChanged(null);
    }
  }

  public void changelistCreatedOrChanged(@NotNull LocalChangeList list) {
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

  @Override
  public void requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myNameTextField, true));
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameTextField;
  }

  protected abstract void nameChanged(@Nls String errorMessage);

  protected ComponentWithTextFieldWrapper createComponentWithTextField(Project project) {
    final EditorTextField editorTextField = createEditorField(project, 1);
    return new ComponentWithTextFieldWrapper(editorTextField) {
      @NotNull
      @Override
      public EditorTextField getEditorTextField() {
        return editorTextField;
      }
    };
  }

  private static EditorTextField createEditorField(final Project project, final int defaultLines) {
    final Set<EditorCustomization> editorFeatures = new HashSet<>();
    ContainerUtil.addIfNotNull(editorFeatures, SpellCheckingEditorCustomizationProvider.getInstance().getEnabledCustomization());
    if (defaultLines == 1) {
      editorFeatures.add(HorizontalScrollBarEditorCustomization.DISABLED);
      editorFeatures.add(OneLineEditorCustomization.ENABLED);
    }
    else {
      editorFeatures.add(SoftWrapsEditorCustomization.ENABLED);
    }
    EditorTextField editorField = EditorTextFieldProvider.getInstance().getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, editorFeatures);

    if (defaultLines > 1) {
      editorField.addSettingsProvider(editor -> editor.getContentComponent()
        .setBorder(new CompoundBorder(editor.getContentComponent().getBorder(), JBUI.Borders.empty(2, 5))));
    }
    return editorField;
  }

  protected abstract static class ComponentWithTextFieldWrapper {
    @NotNull private final Component myComponent;

    public ComponentWithTextFieldWrapper(@NotNull Component component) {
      myComponent = component;
    }

    @NotNull
    abstract EditorTextField getEditorTextField();
  }
}
