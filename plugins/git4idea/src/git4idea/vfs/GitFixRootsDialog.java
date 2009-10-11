/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package git4idea.vfs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.FileStatus;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashSet;
import java.util.TreeSet;

/**
 * This dialog shows a new git root set
 */
class GitFixRootsDialog extends DialogWrapper {
  /**
   * The list of roots
   */
  private JList myGitRoots;
  /**
   * The root panel
   */
  private JPanel myPanel;

  /**
   * The constructor
   *
   * @param project the context project
   */
  protected GitFixRootsDialog(Project project, HashSet<String> current, HashSet<String> added, HashSet<String> removed) {
    super(project, true);
    setTitle(GitBundle.getString("fix.roots.title"));
    setOKButtonText(GitBundle.getString("fix.roots.button"));
    TreeSet<Item> items = new TreeSet<Item>();
    for (String f : added) {
      items.add(new Item(f, FileStatus.ADDED));
    }
    for (String f : current) {
      items.add(new Item(f, removed.contains(f) ? FileStatus.DELETED : FileStatus.NOT_CHANGED));
    }
    DefaultListModel listModel = new DefaultListModel();
    for (Item i : items) {
      listModel.addElement(i);
    }
    myGitRoots.setModel(listModel);
    init();
  }

  /**
   * {@inheritDoc}
   */
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * The item in the list
   */
  private class Item implements Comparable<Item> {
    /**
     * The status of the file
     */
    @NotNull final FileStatus status;
    /**
     * The file name
     */
    @NotNull final String fileName;

    /**
     * The constructor
     *
     * @param fileName the root path
     * @param status   the root status
     */
    public Item(@NotNull String fileName, @NotNull FileStatus status) {
      this.fileName = fileName;
      this.status = status;
    }

    /**
     * {@inheritDoc}
     */
    public int compareTo(Item o) {
      return fileName.compareTo(o.fileName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      if (status == FileStatus.ADDED) {
        return "<html><b>" + fileName + "</b></html>";
      }
      else if (status == FileStatus.DELETED) {
        return "<html><strike>" + fileName + "</strike></html>";
      }
      else {
        return fileName;
      }
    }
  }
}
