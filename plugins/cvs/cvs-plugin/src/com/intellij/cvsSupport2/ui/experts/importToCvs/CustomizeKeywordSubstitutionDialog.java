/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.ImportConfiguration;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * author: lesya, Bas Leijdekkers
 */
public class CustomizeKeywordSubstitutionDialog extends DialogWrapper {

  private static final DefaultCellEditor EDITOR = new DefaultCellEditor(new JComboBox(KeywordSubstitutionWrapper.values().toArray()));
  private static final DefaultTableCellRenderer RENDERER = new DefaultTableCellRenderer();
  private static final ColumnInfo KEYWORD_SUBSTITUTION = new ColumnInfo<FileExtension, KeywordSubstitutionWrapper>(
    CvsBundle.message("import.wizard.keyword.substitution.column.name")) {

    @Override
    public KeywordSubstitutionWrapper valueOf(FileExtension extension) {
      return extension.getKeywordSubstitution();
    }

    @Override
    public Comparator<FileExtension> getComparator() {
      return (extension1, extension2) -> {
        final KeywordSubstitutionWrapper firstSubstitution = extension1.getKeywordSubstitutionsWithSelection().getSelection();
        final KeywordSubstitutionWrapper secondSubstitution = extension2.getKeywordSubstitutionsWithSelection().getSelection();
        return firstSubstitution.toString().compareTo(secondSubstitution.toString());
      };
    }

    @Override
    public boolean isCellEditable(FileExtension extension) {
      return true;
    }

    @Override
    public void setValue(FileExtension extension, KeywordSubstitutionWrapper aValue) {
      extension.setKeywordSubstitution(aValue);
    }

    @Override
    public TableCellRenderer getRenderer(FileExtension extension) {
      return RENDERER;
    }

    @Override
    public TableCellEditor getEditor(FileExtension extension) {
      return EDITOR;
    }

    @Override
    public int getAdditionalWidth() {
      return 20;
    }

    @Override
    public String getMaxStringValue() {
      return KeywordSubstitutionWrapper.KEYWORD_EXPANSION_LOCKER.toString();
    }
  };

  private final static ColumnInfo EXTENSION_COLUMN = new ColumnInfo<FileExtension, String>(
    CvsBundle.message("import.wizard.file.extension.column.name")) {
    @Override
    public String valueOf(FileExtension o) {
      return o.getExtension();
    }

    @Override
    public Comparator<FileExtension> getComparator() {
      return (extension1, extension2) -> extension1.getExtension().compareTo(extension2.getExtension());
    }

    @Override
    public int getAdditionalWidth() {
      return 50;
    }

    @Override
    public String getMaxStringValue() {
      return getName();
    }
  };

  private final static ColumnInfo[] COLUMNS = new ColumnInfo[]{
    EXTENSION_COLUMN, KEYWORD_SUBSTITUTION
  };

  private final ListTableModel<FileExtension> myModel;
  private final ImportConfiguration myImportConfiguration;

  public CustomizeKeywordSubstitutionDialog(Project project, String description, ImportConfiguration importConfiguration) {
    super(project);
    setTitle(description);
    myImportConfiguration = importConfiguration;
    myModel = new ListTableModel<>(COLUMNS);
    myModel.setItems(collectFileTypes());
    init();
    pack();
  }

  private List<FileExtension> collectFileTypes() {
    final Collection<FileExtension> storedExtensions = myImportConfiguration.getExtensions();

    final ArrayList<FileExtension> result = new ArrayList<>();
    result.addAll(storedExtensions);
    final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : fileTypes) {
      final String[] extensions = FileTypeManager.getInstance().getAssociatedExtensions(fileType);
      for (String extension : extensions) {
        final FileExtension fileExtension = new FileExtension(extension);
        if (!result.contains(fileExtension)) result.add(fileExtension);
      }
    }
    return result;
  }

  @Override
  protected void doOKAction() {
    myImportConfiguration.setExtensions(myModel.getItems());
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    final TableView<FileExtension> table = new TableView<>(myModel);
    final Dimension preferredSize = table.getPreferredSize();
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(table);
    final Dimension scrollPaneSize = scrollPane.getPreferredSize();
    scrollPaneSize.width = preferredSize.width;
    scrollPane.setPreferredSize(scrollPaneSize);
    return scrollPane;
  }

  public List<FileExtension> getFileExtensions() {
    return myModel.getItems();
  }
}
