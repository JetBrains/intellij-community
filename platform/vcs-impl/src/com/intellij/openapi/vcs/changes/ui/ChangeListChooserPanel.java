// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.NullableConsumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collection;

public class ChangeListChooserPanel extends JPanel {

  private final MyEditorComboBox myExistingListsCombo;
  private final NewEditChangelistPanel myListPanel;
  private final NullableConsumer<? super String> myOkEnabledListener;
  private final Project myProject;
  private String myLastTypedDescription;
  private boolean myNewNameSuggested = false;
  @Nullable private ChangeListData myData;

  public ChangeListChooserPanel(final Project project, @NotNull final NullableConsumer<? super String> okEnabledListener) {
    super(new BorderLayout());
    myProject = project;
    myExistingListsCombo = new MyEditorComboBox();
    myExistingListsCombo.setEditable(true);
    myExistingListsCombo.setRenderer(new ColoredListCellRenderer<ChangeList>() {

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
      @CalledInAwt
      protected void nameChanged(String errorMessage) {
        //invoke later because of undo manager problem: when you try to undo changelist after description was already changed manually
        ApplicationManager.getApplication().invokeLater(() -> updateDescription(), ModalityState.current());
        myOkEnabledListener.consume(errorMessage);
      }

      @Override
      public void init(LocalChangeList initial) {
        super.init(initial);
        myDescriptionTextArea.addFocusListener(new FocusAdapter() {
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
      protected void nameChangedImpl(Project project, LocalChangeList initial) {
        nameChanged(StringUtil.isEmptyOrSpaces(getChangeListName()) ? "Cannot create new changelist with empty name." : null);
      }
    };
    myOkEnabledListener = okEnabledListener;
    add(myListPanel, BorderLayout.CENTER);
  }

  public void init() {
    myListPanel.init(null);
  }

  public void setChangeLists(Collection<? extends ChangeList> changeLists) {
    myExistingListsCombo.setModel(new DefaultComboBoxModel<>(changeLists.toArray(new ChangeList[0])));
  }

  public void setSuggestedName(@NotNull String name) {
    setSuggestedName(name, null);
  }

  public void setSuggestedName(@NotNull String name, @Nullable String comment) {
    if (StringUtil.isEmptyOrSpaces(name)) return;
    LocalChangeList changelistByName = getExistingChangelistByName(name);
    if (changelistByName != null) {
      myExistingListsCombo.setSelectedItem(changelistByName);
    }
    else {
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
      myListPanel.nameChangedImpl(myProject, null);
    }
  }

  /**
   * Method used as getResult, usually invoked inside doOkAction
   */
  @Nullable
  public LocalChangeList getSelectedList(Project project) {
    ChangeListManager manager = ChangeListManager.getInstance(project);
    String changeListName = myListPanel.getChangeListName();
    LocalChangeList localChangeList = manager.findChangeList(changeListName);

    if (localChangeList == null) {
      localChangeList = ((ChangeListManagerEx)manager).addChangeList(changeListName, myListPanel.getDescription(), myData);
      myListPanel.changelistCreatedOrChanged(localChangeList);
    }
    else {
      //update description if changed
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

  private void updateDescription() {
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
      TextIcon icon = new TextIcon("New", fg, bg, JBUIScale.scale(2));
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
      ObjectUtils.assertNotNull(myEditorTextField.getDocument()).putUserData(ChangeListCompletionContributor.COMBO_BOX_KEY, this);
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
