// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.safeDelete;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.RefactoringDescriptionLocation;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsagePreviewPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/*
*  Mostly copied from com.intellij.refactoring.safeDelete.OverridingMethodsDialog
*  Revision: 14aa2e2
*  (replace PsiMethod formatting)
*/
class KotlinOverridingDialog extends DialogWrapper {
    private final List<UsageInfo> myOverridingMethods;
    private final String[] myMethodText;
    private final boolean[] myChecked;

    private static final int CHECK_COLUMN = 0;
    private JBTable myTable;
    private final UsagePreviewPanel myUsagePreviewPanel;

    KotlinOverridingDialog(Project project, List<UsageInfo> overridingMethods) {
        super(project, true);
        myOverridingMethods = overridingMethods;
        myChecked = new boolean[myOverridingMethods.size()];
        Arrays.fill(myChecked, true);

        myMethodText = new String[myOverridingMethods.size()];
        for (int i = 0; i < myMethodText.length; i++) {
            myMethodText[i] = HtmlChunk.html()
                    .addRaw(ElementDescriptionUtil.getElementDescription(((KotlinSafeDeleteOverridingUsageInfo) myOverridingMethods.get(i)).getOverridingElement(), 
                                                                          RefactoringDescriptionLocation.WITH_PARENT))
                    .toString();
        }
        myUsagePreviewPanel = new UsagePreviewPanel(project, new UsageViewPresentation());
        setTitle(KotlinBundle.message("override.declaration.unused.overriding.methods.title"));
        init();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "#org.jetbrains.kotlin.idea.refactoring.safeDelete.KotlinOverridingDialog";
    }

    @NotNull
    public List<UsageInfo> getSelected() {
        List<UsageInfo> result = new ArrayList<>();
        for (int i = 0; i < myChecked.length; i++) {
            if (myChecked[i]) {
                result.add(myOverridingMethods.get(i));
            }
        }
        return result;
    }

    @NotNull
    @Override
    protected Action[] createActions() {
        return new Action[] {getOKAction(), getCancelAction()};
    }

    @Override
    protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(HelpID.SAFE_DELETE_OVERRIDING);
    }

    @Override
    protected JComponent createNorthPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel(KotlinBundle.message("override.declaration.unused.overriding.methods.description")));
        panel.add(new JLabel(KotlinBundle.message("override.declaration.choose.to.delete")));
        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myTable;
    }

    @Override
    protected void dispose() {
        Disposer.dispose(myUsagePreviewPanel);
        super.dispose();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        final MyTableModel tableModel = new MyTableModel();
        myTable = new JBTable(tableModel);
        myTable.setShowGrid(false);

        TableColumnModel columnModel = myTable.getColumnModel();
        int checkBoxWidth = new JCheckBox().getPreferredSize().width;
        columnModel.getColumn(CHECK_COLUMN).setCellRenderer(new BooleanTableCellRenderer());
        columnModel.getColumn(CHECK_COLUMN).setMaxWidth(checkBoxWidth);
        columnModel.getColumn(CHECK_COLUMN).setMinWidth(checkBoxWidth);


        // make SPACE check/uncheck selected rows
        InputMap inputMap = myTable.getInputMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
        ActionMap actionMap = myTable.getActionMap();
        actionMap.put("enable_disable", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (myTable.isEditing()) return;
                int[] rows = myTable.getSelectedRows();
                if (rows.length > 0) {
                    boolean valueToBeSet = false;
                    for (int row : rows) {
                        if (!myChecked[row]) {
                            valueToBeSet = true;
                            break;
                        }
                    }
                    for (int row : rows) {
                        myChecked[row] = valueToBeSet;
                    }

                    tableModel.updateData();
                }
            }
        });

        panel.setLayout(new BorderLayout());

        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);

        panel.add(scrollPane, BorderLayout.CENTER);
        ListSelectionListener selectionListener = new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                int index = myTable.getSelectionModel().getLeadSelectionIndex();
                if (index != -1) {
                    UsageInfo usageInfo = myOverridingMethods.get(index);
                    myUsagePreviewPanel.updateLayout(Collections.singletonList(usageInfo));
                }
                else {
                    myUsagePreviewPanel.updateLayout(null);
                }
            }
        };
        myTable.getSelectionModel().addListSelectionListener(selectionListener);

        final Splitter splitter = new Splitter(true, 0.3f);
        splitter.setFirstComponent(panel);
        splitter.setSecondComponent(myUsagePreviewPanel);
        myUsagePreviewPanel.updateLayout(null);

        Disposer.register(myDisposable, new Disposable() {
            @Override
            public void dispose() {
                splitter.dispose();
            }
        });

        if (tableModel.getRowCount() != 0) {
            myTable.getSelectionModel().addSelectionInterval(0, 0);
        }
        return splitter;
    }

    class MyTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return myChecked.length;
        }

        @Override
        public String getColumnName(int column) {
            if (column == CHECK_COLUMN) {
                return " ";
            }
            return KotlinBundle.message("override.declaration.member");
        }

        @Override
        public Class getColumnClass(int columnIndex) {
            return columnIndex == CHECK_COLUMN ? Boolean.class : String.class;
        }


        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == CHECK_COLUMN) {
                return Boolean.valueOf(myChecked[rowIndex]);
            }
            else {
                return myMethodText[rowIndex];
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == CHECK_COLUMN) {
                myChecked[rowIndex] = ((Boolean) aValue).booleanValue();
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == CHECK_COLUMN;
        }

        void updateData() {
            fireTableDataChanged();
        }
    }
}

