// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

public final class ChangeListChooserPanel extends JPanel {
  private final MyEditorComboBox myExistingListsCombo;
  private final NewEditChangelistPanel myListPanel;
  private final Consumer<? super String> myOkEnabledListener;
  private final Project myProject;
  private String myLastTypedDescription;
  private boolean myNewNameSuggested = false;
  @Nullable private ChangeListData myData;

  public ChangeListChooserPanel(final Project project, @NotNull Consumer<? super @Nullable String> okEnabledListener) {
    super(new BorderLayout());
    myProject = project;
    myExistingListsCombo = new MyEditorComboBox();
    myExistingListsCombo.setEditable(true);
    myExistingListsCombo.setRenderer(new ColoredListCellRenderer<>() {

      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends ChangeList> list,
                                           ChangeList value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value != null) {
          append(value.getName(), value instanceof LocalChangeList && ((LocalChangeList)value).isDefault()
                                  ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                                  : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });
    myListPanel = new NewEditChangelistPanel(myProject) {

      @Override
      protected NewEditChangelistPanel.ComponentWithTextFieldWrapper createComponentWithTextField(Project project) {
        return new ComponentWithTextFieldWrapper(myExistingListsCombo) {
          @NotNull
          @Override
          public EditorTextField getEditorTextField() {
            return myExistingListsCombo.getEditorTextField();
          }
        };
      }

      @Override
      @RequiresEdt
      protected void nameChanged(String errorMessage) {
        //invoke later because of undo manager problem: when you try to undo changelist after description was already changed manually
        ApplicationManager.getApplication().invokeLater(() -> updateDescription(), ModalityState.current());
        myOkEnabledListener.accept(errorMessage);
      }

      @Override
      public void init(@Nullable LocalChangeList initial) {
        super.init(initial);
        descriptionTextArea.addFocusListener(new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            super.focusLost(e);
            if (getExistingChangelistByName(myListPanel.getChangeListName()) == null) {
              myLastTypedDescription = myListPanel.getDescription();
            }
          }
        });
      }

      @Override
      protected void nameChangedImpl(@Nullable LocalChangeList initial) {
        nameChanged(StringUtil.isEmptyOrSpaces(getChangeListName()) ? VcsBundle.message("new.changelist.empty.name.error") : null);
      }
    };
    myOkEnabledListener = okEnabledListener;
    add(myListPanel, BorderLayout.CENTER);

    setChangeLists(null);
    setDefaultSelection(null);
  }

  public void init() {
    myListPanel.init(null);
  }

  public void setChangeLists(@Nullable Collection<? extends ChangeList> changeLists) {
    if (changeLists == null) changeLists = ChangeListManager.getInstance(myProject).getChangeLists();
    myExistingListsCombo.setModel(new DefaultComboBoxModel<>(changeLists.toArray(new ChangeList[0])));
  }

  public void setSuggestedName(@NlsSafe @NotNull String name) {
    setSuggestedName(name, false);
  }

  public void setSuggestedName(@NlsSafe @NotNull String name, boolean forceCreate) {
    setSuggestedName(name, null, forceCreate);
  }

  public void setSuggestedName(@NlsSafe @NotNull String name, @Nls @Nullable String comment, boolean forceCreate) {
    if (StringUtil.isEmptyOrSpaces(name)) return;
    LocalChangeList changelistByName = getExistingChangelistByName(name);
    if (changelistByName != null) {
      myExistingListsCombo.setSelectedItem(changelistByName);
    }
    else if (forceCreate || VcsApplicationSettings.getInstance().CREATE_CHANGELISTS_AUTOMATICALLY) {
      myNewNameSuggested = true;
      myExistingListsCombo.insertItemAt(LocalChangeList.createEmptyChangeList(myProject, name), 0);
      if (StringUtil.isEmptyOrSpaces(myLastTypedDescription)) {
        myLastTypedDescription = comment;
      }
      if (VcsConfiguration.getInstance(myProject).PRESELECT_EXISTING_CHANGELIST) {
        selectActiveChangeListIfExist();
      }
      else {
        myListPanel.setChangeListName(name);
      }
    }
    updateDescription();
  }

  private void selectActiveChangeListIfExist() {
    myExistingListsCombo.setSelectedItem(ChangeListManager.getInstance(myProject).getDefaultChangeList());
  }

  public void setData(@Nullable ChangeListData data) {
    myData = data;
  }

  public void updateEnabled() {
    if (myProject != null) {
      myListPanel.nameChangedImpl(null);
    }
  }

  /**
   * Method used as getResult, usually invoked inside doOkAction
   */
  @Nullable
  public LocalChangeList getSelectedList(Project project) {
    ChangeListManagerEx manager = ChangeListManagerEx.getInstanceEx(project);
    String changeListName = myListPanel.getChangeListName();
    LocalChangeList localChangeList = manager.findChangeList(changeListName);

    if (localChangeList == null) {
      localChangeList = manager.addChangeList(changeListName, myListPanel.getDescription(), myData);
      myListPanel.changelistCreatedOrChanged(localChangeList);
    }
    else {
      if (!StringUtil.equals(localChangeList.getComment(), myListPanel.getDescription())) {
        VcsStatisticsCollector.CHANGE_LIST_COMMENT_EDITED.log(project, VcsStatisticsCollector.EditChangeListPlace.OTHER);
      }
      manager.editComment(changeListName, myListPanel.getDescription());
    }
    rememberSettings(project, localChangeList.isDefault(), myListPanel.getMakeActiveCheckBox().isSelected());
    if (myListPanel.getMakeActiveCheckBox().isSelected()) {
      manager.setDefaultChangeList(localChangeList);
    }
    return localChangeList;
  }

  private void rememberSettings(@NotNull Project project, boolean activeListSelected, boolean setActive) {
    if (myNewNameSuggested) {
      VcsConfiguration.getInstance(project).PRESELECT_EXISTING_CHANGELIST = activeListSelected;
    }
    VcsConfiguration.getInstance(project).MAKE_NEW_CHANGELIST_ACTIVE = setActive;
  }

  public void setDefaultSelection(final ChangeList defaultSelection) {
    if (defaultSelection == null) {
      selectActiveChangeListIfExist();
    }
    else {
      myExistingListsCombo.setSelectedItem(defaultSelection);
    }
    updateDescription();
    updateEnabled();
  }

  public void setChangeListDescription(String description) {
    myListPanel.setDescription(description);
    myLastTypedDescription = description;
  }

  private void updateDescription() {
    if (myLastTypedDescription != null) return;
    LocalChangeList list = getExistingChangelistByName(myListPanel.getChangeListName());
    String newText = list != null ? list.getComment() : myLastTypedDescription;
    if (!StringUtil.equals(myListPanel.getDescription(), newText)) {
      myListPanel.setDescription(newText);
    }
  }

  private LocalChangeList getExistingChangelistByName(@NotNull String changeListName) {
    ChangeListManager manager = ChangeListManager.getInstance(myProject);
    return manager.findChangeList(changeListName);
  }

  public JComponent getPreferredFocusedComponent() {
    return myExistingListsCombo.getEditorTextField();
  }

  private class MyEditorComboBox extends ComboBox<ChangeList> {

    private static final int PREF_WIDTH = 200;
    private final LanguageTextField myEditorTextField;

    MyEditorComboBox() {
      super(PREF_WIDTH);
      JBColor fg = new JBColor(0x00b53d, 0x6ba65d);
      JBColor bg = new JBColor(0xebfcf1, 0x313b32);
      TextIcon icon = new TextIcon(VcsBundle.message("new.changelist.new.label"), fg, bg, JBUIScale.scale(2));
      icon.setFont(RelativeFont.TINY.derive(getFont()));
      icon.setRound(JBUIScale.scale(4));
      JLabel label = new JLabel(icon);
      JPanel panel = new JPanel(new BorderLayout());
      panel.setOpaque(true);
      panel.setBorder(JBUI.Borders.empty(1, 1, 1, 4));
      panel.add(label, BorderLayout.CENTER);
      myEditorTextField = new LanguageTextField(PlainTextLanguage.INSTANCE, myProject, "");
      myEditorTextField.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent e) {
          String changeListName = e.getDocument().getText();
          panel.setVisible(!StringUtil.isEmptyOrSpaces(changeListName) && getExistingChangelistByName(changeListName) == null);
        }
      });
      Objects.requireNonNull(myEditorTextField.getDocument()).putUserData(ChangeListCompletionContributor.COMBO_BOX_KEY, this);
      ComboBoxCompositeEditor<Object, LanguageTextField> compositeEditor = new ComboBoxCompositeEditor<>(myEditorTextField, panel);
      myEditorTextField.addSettingsProvider((editor) -> {
        Color editorBackgroundColor = editor.getBackgroundColor();
        panel.setBackground(editorBackgroundColor);
        compositeEditor.setBackground(editorBackgroundColor);
      });
      setEditor(compositeEditor);
    }

    @NotNull
    private EditorTextField getEditorTextField() {
      return myEditorTextField;
    }
  }
}
