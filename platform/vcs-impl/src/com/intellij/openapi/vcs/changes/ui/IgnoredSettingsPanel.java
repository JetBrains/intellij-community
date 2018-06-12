/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.IgnoreSettingsType;
import com.intellij.openapi.vcs.changes.IgnoredFileBean;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class IgnoredSettingsPanel implements SearchableConfigurable, Configurable.NoScroll {
  private final JBList<IgnoredFileBean> myList;
  private JPanel myPanel;
  private final Project myProject;
  private DefaultListModel<IgnoredFileBean> myModel = new DefaultListModel<>();
  private final ChangeListManagerImpl myChangeListManager;
  private final Set<String> myDirectoriesManuallyRemovedFromIgnored = new HashSet<>();

  public IgnoredSettingsPanel(Project project) {
    myList = new JBList<>();
    myList.setCellRenderer(new MyCellRenderer());
    myList.getEmptyText().setText(VcsBundle.message("no.ignored.files"));

    new ListSpeedSearch<>(myList, (Function<IgnoredFileBean, String>)bean -> {
      return getBeanTextPresentation(bean);
    });
    myProject = project;
    myChangeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
  }

  private void setItems(final IgnoredFileBean[] filesToIgnore) {
    myModel = new DefaultListModel<>();
    for (IgnoredFileBean bean : filesToIgnore) {
      myModel.addElement(bean);
    }
    myList.setModel(myModel);
  }

  private IgnoredFileBean[] getItems() {
    final int count = myModel.getSize();
    IgnoredFileBean[] result = new IgnoredFileBean[count];
    for (int i = 0; i < count; i++) {
      result[i] = myModel.getElementAt(i);
    }
    return result;
  }

  private void addItem() {
    IgnoreUnversionedDialog dlg = new IgnoreUnversionedDialog(myProject);
    if (dlg.showAndGet()) {
      final IgnoredFileBean[] ignoredFiles = dlg.getSelectedIgnoredFiles();
      for (IgnoredFileBean bean : ignoredFiles) {
        myModel.addElement(bean);
      }
    }
  }

  private void editItem() {
    IgnoredFileBean bean = myList.getSelectedValue();
    if (bean == null) return;
    IgnoreUnversionedDialog dlg = new IgnoreUnversionedDialog(myProject);
    dlg.setIgnoredFile(bean);
    if (dlg.showAndGet()) {
      IgnoredFileBean[] beans = dlg.getSelectedIgnoredFiles();
      assert beans.length == 1;
      int selectedIndex = myList.getSelectedIndex();
      myModel.setElementAt(beans[0], selectedIndex);
    }
  }

  private void deleteItems() {
    for (IgnoredFileBean bean : myList.getSelectedValuesList()) {
      if (bean.getType() == IgnoreSettingsType.UNDER_DIR) {
        myDirectoriesManuallyRemovedFromIgnored.add(bean.getPath());
      }
    }
    ListUtil.removeSelectedItems(myList);
  }

  public void reset() {
    setItems(myChangeListManager.getFilesToIgnore());
    myDirectoriesManuallyRemovedFromIgnored.clear();
    myDirectoriesManuallyRemovedFromIgnored.addAll(myChangeListManager.getIgnoredFilesComponent().getDirectoriesManuallyRemovedFromIgnored());
  }

  public void apply() {
    IgnoredFileBean[] toIgnore = getItems();
    myChangeListManager.setFilesToIgnore(toIgnore);
    for (IgnoredFileBean bean : toIgnore) {
      if (bean.getType() == IgnoreSettingsType.UNDER_DIR) {
        myDirectoriesManuallyRemovedFromIgnored.remove(bean.getPath());
      }
    }
    myChangeListManager.getIgnoredFilesComponent().setDirectoriesManuallyRemovedFromIgnored(myDirectoriesManuallyRemovedFromIgnored);
  }

  public boolean isModified() {
    return !Comparing.equal(myChangeListManager.getFilesToIgnore(), getItems());
  }

  public JComponent createComponent() {
    if (myPanel == null) {
      myPanel = ToolbarDecorator.createDecorator(myList)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            addItem();
          }
        }).setEditAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            editItem();
          }
        }).setRemoveAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            deleteItems();
          }
        }).disableUpDownActions().createPanel();
    }
    return myPanel;
  }

  @Nls
  public String getDisplayName() {
    return "Ignored Files";
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "project.propVCSSupport.Ignored.Files";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }


  @Nullable
  private static String getBeanTextPresentation(IgnoredFileBean bean) {
    IgnoreSettingsType type = bean.getType();

    String path = bean.getPath();
    String mask = bean.getMask();

    if (type == IgnoreSettingsType.UNDER_DIR && path != null) {
      return VcsBundle.message("ignored.configure.item.directory", path);
    }
    if (type == IgnoreSettingsType.FILE && path != null) {
      return VcsBundle.message("ignored.configure.item.file", path);
    }
    if (type == IgnoreSettingsType.MASK && mask != null) {
      return VcsBundle.message("ignored.configure.item.mask", mask);
    }

    return null;
  }

  private static class MyCellRenderer extends ColoredListCellRenderer<IgnoredFileBean> {
    protected void customizeCellRenderer(@NotNull JList list, IgnoredFileBean bean, int index, boolean selected, boolean hasFocus) {
      if (UIUtil.isUnderGTKLookAndFeel()) {
        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        UIUtil.changeBackGround(this, background);
      }

      String text = getBeanTextPresentation(bean);
      if (text != null) {
        append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }

      SpeedSearchUtil.applySpeedSearchHighlighting(list, this, true, selected);
    }
  }
}
