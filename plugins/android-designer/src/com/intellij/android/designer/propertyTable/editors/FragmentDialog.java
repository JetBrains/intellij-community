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
package com.intellij.android.designer.propertyTable.editors;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Alexander Lobas
 */
public class FragmentDialog extends DialogWrapper implements ListSelectionListener {
  private final JList myList = new JList();
  private final JScrollPane myComponent = ScrollPaneFactory.createScrollPane(myList);
  private String myResultFragmentName;

  public FragmentDialog(Module module) {
    super(module.getProject());

    myList.setPreferredSize(new Dimension(500, 400));
    myList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && myList.getSelectedValue() != null) {
          close(OK_EXIT_CODE);
        }
      }
    });

    DefaultListModel model = new DefaultListModel();
    for (PsiClass psiClass : findInheritors(module, "android.app.Fragment")) {
      model.addElement(psiClass.getQualifiedName());
    }
    for (PsiClass psiClass : findInheritors(module, "android.support.v4.app.Fragment")) {
      model.addElement(psiClass.getQualifiedName());
    }
    myList.setModel(model);

    ListSelectionModel selectionModel = myList.getSelectionModel();
    selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selectionModel.addListSelectionListener(this);

    new ListSpeedSearch(myList);

    setTitle("Fragment Dialog");
    getOKAction().setEnabled(false);

    init();
  }

  private static Collection<PsiClass> findInheritors(Module module, String name) {
    Project project = module.getProject();
    PsiClass base = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
    if (base != null) {
      GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
      return ClassInheritorsSearch.search(base, scope, true).findAll();
    }
    return Collections.emptyList();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myComponent;
  }

  public String getFragmentName() {
    return myResultFragmentName;
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    myResultFragmentName = (String)myList.getSelectedValue();
    getOKAction().setEnabled(myResultFragmentName != null);
  }
}