package com.intellij.execution.junit2.configuration;

import com.intellij.ide.structureView.impl.StructureNodeRenderer;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.ui.*;
import com.intellij.execution.ExecutionBundle;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;

// Author: dyoma

public class MethodList extends JPanel {
  private final PsiClass myClass;
  private static final Comparator<PsiMethod> METHOD_NAME_COMPARATOR = new Comparator<PsiMethod>() {
      public int compare(final PsiMethod psiMethod, final PsiMethod psiMethod1) {
        return psiMethod.getName().compareToIgnoreCase(psiMethod1.getName());
      }
    };
  private final SortedListModel<PsiMethod> myListModel = new SortedListModel<PsiMethod>(METHOD_NAME_COMPARATOR);
  private final JList myList = new JList(myListModel);

  public MethodList(final PsiClass psiClass, final Condition<PsiMethod> filter) {
    super(new BorderLayout());
    myClass = psiClass;
    createList(psiClass.getAllMethods(), filter);
    add(ScrollPaneFactory.createScrollPane(myList));
    myList.setCellRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(final JList list, final Object value, final int index, final boolean selected, final boolean hasFocus) {
        final PsiMethod psiMethod = (PsiMethod)value;
        append(PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME, 0),
               StructureNodeRenderer.applyDeprecation(psiMethod, SimpleTextAttributes.REGULAR_ATTRIBUTES));
        final PsiClass containingClass = psiMethod.getContainingClass();
        if (!myClass.equals(containingClass))
          append(" (" + containingClass.getQualifiedName() + ")",
                 StructureNodeRenderer.applyDeprecation(containingClass, SimpleTextAttributes.GRAY_ATTRIBUTES));
      }
    });
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    ListScrollingUtil.ensureSelectionExists(myList);
  }

  private void createList(final PsiMethod[] allMethods, final Condition<PsiMethod> filter) {
    for (int i = 0; i < allMethods.length; i++) {
      final PsiMethod method = allMethods[i];
      if (filter.value(method)) myListModel.add(method);
    }
  }

  public PsiMethod getSelected() {
    return (PsiMethod)myList.getSelectedValue();
  }

  public static PsiMethod showDialog(final PsiClass psiClass, final Condition<PsiMethod> filter, final JComponent parent) {
    final MethodList methodList = new MethodList(psiClass, filter);
    final DialogBuilder builder = new DialogBuilder(parent);
    builder.setCenterPanel(methodList);
    builder.setPreferedFocusComponent(methodList.myList);
    builder.setTitle(ExecutionBundle.message("choose.test.method.dialog.title"));
    return builder.show() == DialogWrapper.OK_EXIT_CODE ? methodList.getSelected() : null;
  }
}
