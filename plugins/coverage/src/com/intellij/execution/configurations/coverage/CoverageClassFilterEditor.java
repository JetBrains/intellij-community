// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations.coverage;

import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.PackageChooser;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.classFilter.ClassFilterEditor;
import org.jetbrains.annotations.Nullable;

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
      new PackageChooserDialog(JavaCoverageBundle.message("coverage.pattern.filter.editor.choose.package.title"), myProject) {
        @Override
        protected @Nullable PsiPackage getPsiPackage(String newQualifiedName) {
          return JavaPsiFacade.getInstance(myProject).findPackage(newQualifiedName);
        }

        @Override
        protected boolean canExpandInSpeedSearch() {
          return true;
        }
      };
    if (chooser.showAndGet()) {
      List<PsiPackage> packages = chooser.getSelectedPackages();
      if (!packages.isEmpty()) {
        for (final PsiPackage aPackage : packages) {
          final String fqName = aPackage.getQualifiedName();
          final String pattern = fqName.isEmpty() ? "*" : fqName + ".*";
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
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Package);
  }
}
