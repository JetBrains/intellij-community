package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.cvsSupport2.config.ImportConfiguration;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.cvsSupport2.ui.experts.CvsWizard;
import com.intellij.cvsSupport2.ui.experts.WizardStep;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ComboBoxTableCellEditor;
import com.intellij.util.ui.ComboBoxTableCellRenderer;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * author: lesya
 */
public class CustomizeKeywordSubstitutionStep extends WizardStep {
  private static final ColumnInfo KEYWORD_SUBSTITUTION = new ColumnInfo("Keyword Substitution") {
    public Object valueOf(Object object) {
      return ((FileExtension)object).getKeywordSubstitutionsWithSelection();
    }

    public Comparator getComparator() {
      return new Comparator() {
        public int compare(Object o1, Object o2) {
          KeywordSubstitutionWrapper firstSubstitution = (KeywordSubstitutionWrapper)((FileExtension)o1).getKeywordSubstitutionsWithSelection()
            .getSelection();
          KeywordSubstitutionWrapper secondSubstitution = (KeywordSubstitutionWrapper)((FileExtension)o2).getKeywordSubstitutionsWithSelection()
            .getSelection();
          return
            firstSubstitution.toString().compareTo(secondSubstitution.toString());
        }
      };
    }

    public boolean isCellEditable(Object o) {
      return true;
    }

    public void setValue(Object o, Object aValue) {
      ((FileExtension)o).setKeywordSubstitution(((KeywordSubstitutionWrapper)aValue));
    }

    public TableCellRenderer getRenderer(Object o) {
      return ComboBoxTableCellRenderer.INSTANCE;
    }

    public TableCellEditor getEditor(Object item) {
      return ComboBoxTableCellEditor.INSTANCE;
    }
  };

  private final static ColumnInfo EXTENSION_COLUMN = new ColumnInfo("Extension") {
    public Object valueOf(Object o) {
      return ((FileExtension)o).getExtension();
    }

    public Comparator getComparator() {
      return new Comparator(){
        public int compare(Object o, Object o1) {
          return ((FileExtension)o).getExtension()
            .compareTo(((FileExtension)o1).getExtension());
        };
      };
    }
  };

  private final static ColumnInfo[] COLUMNS = new ColumnInfo[]{
    EXTENSION_COLUMN, KEYWORD_SUBSTITUTION
  };

  private final TableView myTable;
  private ListTableModel myModel;
  private final ImportConfiguration myImportConfiguration;

  public CustomizeKeywordSubstitutionStep(String description, CvsWizard wizard,
                                          ImportConfiguration importConfiguration) {
    super(description, wizard);
    myModel = new ListTableModel(COLUMNS);
    myTable = new TableView(myModel);
    myTable.setMinRowHeight(new JComboBox().getPreferredSize().height + 2);
    myImportConfiguration = importConfiguration;
    myModel.setItems(collectFileTypes());
    init();
  }

  protected void dispose() {
  }

  private List collectFileTypes() {
    Collection<FileExtension> storedExtensions = myImportConfiguration.getExtensions();

    ArrayList result = new ArrayList();
    result.addAll(storedExtensions);
    FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (int i = 0; i < fileTypes.length; i++) {
      FileType fileType = fileTypes[i];
      String[] extensions = FileTypeManager.getInstance().getAssociatedExtensions(fileType);
      for (int j = 0; j < extensions.length; j++) {
        FileExtension fileExtension = new FileExtension(extensions[j]);
        if (!result.contains(fileExtension))
          result.add(fileExtension);
      }
    }
    return result;
  }

  public void saveState() {
    myImportConfiguration.setExtensions(myModel.getItems());
  }

  public boolean nextIsEnabled() {
    return true;
  }

  public boolean setActive() {
    return true;
  }

  protected JComponent createComponent() {
    return ScrollPaneFactory.createScrollPane(myTable);
  }

  public List getFileExtensions() {
    return myModel.getItems();
  }


}
