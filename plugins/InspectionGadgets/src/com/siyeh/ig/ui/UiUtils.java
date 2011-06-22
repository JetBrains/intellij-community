/*
 * Copyright 2010-2011 Bas Leijdekkers
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
package com.siyeh.ig.ui;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.PlatformIcons;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;

public class UiUtils {

    private UiUtils() {}

    public static ActionToolbar createAddRemoveToolbar(ListTable table) {
        final AnAction addAction = new AddAction(table);
        final AnAction removeAction = new RemoveAction(table);
        final ActionGroup group =
                new DefaultActionGroup(addAction, removeAction);
        final ActionManager actionManager = ActionManager.getInstance();
        return actionManager.createActionToolbar(ActionPlaces.UNKNOWN,
                group, true);
    }

    public static ActionToolbar createAddRemoveTreeClassChooserToolbar(
            ListTable table, String chooserTitle,
            @NonNls String... ancestorClasses) {
        final ClassFilter filter;
        if (ancestorClasses.length == 0) {
            filter = ClassFilter.ALL;
        } else {
            filter = new SubclassFilter(ancestorClasses);
        }
        final AnAction addAction = new TreeClassChooserAction(table,
                chooserTitle, filter);
        final AnAction removeAction = new RemoveAction(table);
        final ActionGroup group =
                new DefaultActionGroup(addAction, removeAction);
        final ActionManager actionManager = ActionManager.getInstance();
        return actionManager.createActionToolbar(ActionPlaces.UNKNOWN,
                group, true);
    }

    public static ActionToolbar createAddRemoveTreeAnnotationChooserToolbar(
            ListTable table, String chooserTitle) {
        final ClassFilter filter =
                new ClassFilter() {
                    public boolean isAccepted(PsiClass psiClass) {
                        return psiClass.isAnnotationType();
                    }
                };
        final AnAction addAction = new TreeClassChooserAction(table,
                chooserTitle, filter);
        final AnAction removeAction = new RemoveAction(table);
        final ActionGroup group =
                new DefaultActionGroup(addAction, removeAction);
        final ActionManager actionManager = ActionManager.getInstance();
        return actionManager.createActionToolbar(ActionPlaces.UNKNOWN,
                group, true);
    }

    private static class TreeClassChooserAction extends AnAction {

        private final ListTable table;
        private final String chooserTitle;
        private final ClassFilter myFilter;

        public TreeClassChooserAction(
                @NotNull ListTable table, @NotNull String chooserTitle,
                @NotNull ClassFilter filter) {
            super(InspectionGadgetsBundle.message("button.add"), "",
                    PlatformIcons.ADD_ICON);
            this.table = table;
            this.chooserTitle = chooserTitle;
            this.myFilter = filter;
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            final DataContext dataContext = e.getDataContext();
            final Project project = DataKeys.PROJECT.getData(dataContext);
            if (project == null) {
                return;
            }
            final TreeClassChooserFactory chooserFactory =
                    TreeClassChooserFactory.getInstance(project);
            final TreeClassChooser classChooser =
                    chooserFactory.createWithInnerClassesScopeChooser(chooserTitle,
                            GlobalSearchScope.allScope(project), myFilter, null);
            classChooser.showDialog();
            final PsiClass selectedClass = classChooser.getSelected();
            if (selectedClass == null) {
                return;
            }
            final String qualifiedName = selectedClass.getQualifiedName();
            final ListWrappingTableModel tableModel = table.getModel();
            final int index = tableModel.indexOf(qualifiedName, 0);
            final int rowIndex;
            if (index < 0) {
                tableModel.addRow(qualifiedName);
                rowIndex = tableModel.getRowCount() - 1;
            } else {
                rowIndex = index;
            }
            final ListSelectionModel selectionModel =
                    table.getSelectionModel();
            selectionModel.setSelectionInterval(rowIndex, rowIndex);
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    final Rectangle rectangle =
                            table.getCellRect(rowIndex, 0, true);
                    table.scrollRectToVisible(rectangle);
                }
            });
        }
    }

    private static class AddAction extends AnAction {

        private final ListTable table;

        public AddAction(ListTable table) {
            super(InspectionGadgetsBundle.message("button.add"), "",
                    PlatformIcons.ADD_ICON);
            this.table = table;
        }

        @Override
        public void actionPerformed(AnActionEvent anActionEvent) {
            final ListWrappingTableModel tableModel = table.getModel();
            tableModel.addRow();
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    final int lastRowIndex = tableModel.getRowCount() - 1;
                    final Rectangle rectangle =
                            table.getCellRect(lastRowIndex, 0, true);
                    table.scrollRectToVisible(rectangle);
                    table.editCellAt(lastRowIndex, 0);
                    final ListSelectionModel selectionModel =
                            table.getSelectionModel();
                    selectionModel.setSelectionInterval(lastRowIndex,
                            lastRowIndex);
                    final TableCellEditor editor = table.getCellEditor();
                    final Component component =
                            editor.getTableCellEditorComponent(table,
                                    null, true, lastRowIndex, 0);
                    component.requestFocus();
                }
            });
        }
    }

    private static class RemoveAction extends AnAction {

        private final ListTable table;

        public RemoveAction(ListTable table) {
            super(InspectionGadgetsBundle.message("button.remove"), "",
                    PlatformIcons.DELETE_ICON);
            this.table = table;
        }

        @Override
        public void actionPerformed(AnActionEvent anActionEvent) {
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    final TableCellEditor editor = table.getCellEditor();
                    if (editor != null) {
                        editor.stopCellEditing();
                    }
                    final ListSelectionModel selectionModel =
                            table.getSelectionModel();
                    final int minIndex = selectionModel.getMinSelectionIndex();
                    final int maxIndex = selectionModel.getMaxSelectionIndex();
                    if (minIndex == -1 || maxIndex == -1) {
                        return;
                    }
                    final ListWrappingTableModel tableModel = table.getModel();
                    for (int i = minIndex; i <= maxIndex; i++) {
                        if (selectionModel.isSelectedIndex(i)) {
                            tableModel.removeRow(i);
                        }
                    }
                    final int count = tableModel.getRowCount();
                    if (count <= minIndex) {
                        selectionModel.setSelectionInterval(count - 1,
                                count - 1);
                    } else if (minIndex <= 0) {
                        if (count > 0) {
                            selectionModel.setSelectionInterval(0, 0);
                        }
                    } else {
                        selectionModel.setSelectionInterval(minIndex - 1,
                                minIndex - 1);
                    }
                }
            });
        }

        @Override
        public void update(final AnActionEvent e) {
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    final ListSelectionModel selectionModel =
                            table.getSelectionModel();
                    final int minIndex = selectionModel.getMinSelectionIndex();
                    final int maxIndex = selectionModel.getMaxSelectionIndex();
                    if (minIndex == -1 || maxIndex == -1) {
                        e.getPresentation().setEnabled(false);
                    } else {
                        e.getPresentation().setEnabled(true);
                    }
                }
            });
        }
    }

    private static class SubclassFilter implements ClassFilter {

        private final String[] ancestorClasses;

        private SubclassFilter(String[] ancestorClasses) {
            this.ancestorClasses = ancestorClasses;
        }

        public boolean isAccepted(PsiClass aClass) {
            for (String ancestorClass : ancestorClasses) {
                if (InheritanceUtil.isInheritor(aClass, ancestorClass)) {
                    return true;
                }
            }
            return false;
        }
    }
}
