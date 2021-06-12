// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations.coverage;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.PackageChooser;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.classFilter.ClassFilterEditor;

import javax.swing.*;
import java.util.List;

class CoverageClassFilterEditor extends ClassFilterEditor {
  CoverageClassFilterEditor(Project project) {
    super(project, new ClassFilter() {
      @Override
      public boolean isAccepted(PsiClass aClass) {
        if (aClass.getContainingClass() != null) return false;
        return true;
      }
    }, null);
    myTableModel.setEditEnabled(false);
    myTable.setVisibleRowCount(4);
  }

  @Override
  protected void addPatternFilter() {
    PackageChooser chooser =
      new PackageChooserDialog(JavaCoverageBundle.message("coverage.pattern.filter.editor.choose.package.title"), myProject);
    if (chooser.showAndGet()) {
      List<PsiPackage> packages = chooser.getSelectedPackages();
      if (!packages.isEmpty()) {
        for (final PsiPackage aPackage : packages) {
          final String fqName = aPackage.getQualifiedName();
          final String pattern = fqName.length() > 0 ? fqName + ".*" : "*";
          myTableModel.addRow(createFilter(pattern));
        }
        int row = myTableModel.getRowCount() - 1;
        myTable.getSelectionModel().setSelectionInterval(row, row);
        myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTable, true));
      }
    }
  }

  @Override
  protected String getAddPatternButtonText() {
    return JavaCoverageBundle.message("coverage.button.add.package");
  }

  @Override
  protected Icon getAddPatternButtonIcon() {
    return AllIcons.Nodes.Package;
  }
}
