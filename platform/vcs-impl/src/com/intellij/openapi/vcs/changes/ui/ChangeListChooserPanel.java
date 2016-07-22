/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListRenderer;
import com.intellij.ui.*;
import com.intellij.util.NullableConsumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInsight.completion.ComboEditorCompletionContributor.CONTINUE_RUN_COMPLETION;

public class ChangeListChooserPanel extends JPanel {

  private final MyEditorComboBox myExistingListsCombo;
  private final NewEditChangelistPanel myListPanel;
  private final NullableConsumer<String> myOkEnabledListener;
  private final Project myProject;
  private String myLastTypedDescription;

  public ChangeListChooserPanel(final Project project, @NotNull final NullableConsumer<String> okEnabledListener) {
    super(new BorderLayout());
    myProject = project;
    myExistingListsCombo = new MyEditorComboBox(project);
    myExistingListsCombo.setEditable(true);
    myExistingListsCombo.setRenderer(new ColoredListCellRenderer<String>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends String> list,
                                           String value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value != null) {
          String name = value;
          LocalChangeList changeList = ChangeListManager.getInstance(myProject).findChangeList(name);
          int visibleWidth = myExistingListsCombo.getEditorTextField().getVisibleRect().width;
          if (visibleWidth == 0) {
            name = name.length() > 10 ? name.substring(0, 7) + " .." : name;
          }
          else {
            final FontMetrics fm = list.getFontMetrics(list.getFont());
            final int width = fm.stringWidth(name);
            if ((visibleWidth > 0) && (width > visibleWidth)) {
              final String truncated = CommittedChangeListRenderer
                .truncateDescription(name, fm, visibleWidth - fm.stringWidth(" ..") - 7);
              if (truncated.length() > 5) {
                name = truncated + " ..";
              }
            }
          }
          append(name, changeList != null && changeList.isDefault()
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
            if (getExistingChangelist() == null) {
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
    List<String> changelistNames = ContainerUtil.map(changeLists, ChangeList::getName);
    Collections.sort(changelistNames);
    myExistingListsCombo.setModel(new CollectionComboBoxModel<>(changelistNames));
  }

  public void setDefaultName(String name) {
    if (!StringUtil.isEmptyOrSpaces(name)) {
      myListPanel.setChangeListName(name);
    }
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
      localChangeList = manager.addChangeList(changeListName, myListPanel.getDescription());
      myListPanel.changelistCreatedOrChanged(localChangeList);
    }
    else {
      //update description if changed
      localChangeList.setComment(myListPanel.getDescription());
    }
    if (myListPanel.getMakeActiveCheckBox().isSelected()) {
      manager.setDefaultChangeList(localChangeList);
    }
    VcsConfiguration.getInstance(project).MAKE_NEW_CHANGELIST_ACTIVE = myListPanel.getMakeActiveCheckBox().isSelected();
    return localChangeList;
  }

  public void setDefaultSelection(final ChangeList defaultSelection) {
    if (defaultSelection == null) {
      myExistingListsCombo.setSelectedIndex(0);
    }
    else {
      myExistingListsCombo.setSelectedItem(defaultSelection.getName());
    }
    updateDescription();
    updateEnabled();
  }

  private void updateDescription() {
    LocalChangeList list = getExistingChangelist();
    String newText = list != null ? list.getComment() : myLastTypedDescription;
    if (!StringUtil.equals(myListPanel.getDescription(), newText)) {
      myListPanel.setDescription(newText);
    }
  }

  private LocalChangeList getExistingChangelist() {
    ChangeListManager manager = ChangeListManager.getInstance(myProject);
    String changeListName = myListPanel.getChangeListName();
    return manager.findChangeList(changeListName);
  }

  public JComponent getPreferredFocusedComponent() {
    return myExistingListsCombo;
  }

  private static class MyEditorComboBox extends ComboBox<String> {

    public MyEditorComboBox(Project project) {
      super();
      setEditor(new StringComboboxEditor(project, FileTypes.PLAIN_TEXT, this) {
        @Override
        protected void onEditorCreate(EditorEx editor) {
          super.onEditorCreate(editor);
          getDocument().putUserData(CONTINUE_RUN_COMPLETION, true);
        }
      });
    }

    @NotNull
    private EditorTextField getEditorTextField() {
      return ObjectUtils.assertNotNull((EditorTextField)getEditor().getEditorComponent());
    }
  }
}
