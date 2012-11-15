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

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Alexander Lobas
 */
public class ChooseClassDialog extends DialogWrapper implements ListSelectionListener {
  private final JList myList = new JBList();
  private final JScrollPane myComponent = ScrollPaneFactory.createScrollPane(myList);
  private String myResultClassName;

  public ChooseClassDialog(Module module, String title, boolean includeAll, String... classes) {
    super(module.getProject());

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        if (myList.getSelectedValue() != null) {
          close(OK_EXIT_CODE);
          return true;
        }
        return false;
      }
    }.installOn(myList);

    DefaultListModel model = new DefaultListModel();
    findClasses(module, includeAll, model, classes);
    myList.setModel(model);
    myList.setCellRenderer(new DefaultPsiElementCellRenderer());

    ListSelectionModel selectionModel = myList.getSelectionModel();
    selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selectionModel.addListSelectionListener(this);

    new ListSpeedSearch(myList) {
      @Override
      protected boolean isMatchingElement(Object element, String pattern) {
        PsiClass psiClass = (PsiClass)element;
        return compare(psiClass.getName(), pattern) || compare(psiClass.getQualifiedName(), pattern);
      }
    };

    setTitle(title);
    setOKActionEnabled(false);

    init();
  }

  protected void findClasses(Module module, boolean includeAll, DefaultListModel model, String[] classes) {
    for (String className : classes) {
      for (PsiClass psiClass : findInheritors(module, className, includeAll)) {
        model.addElement(psiClass);
      }
    }
  }

  protected static Collection<PsiClass> findInheritors(Module module, String name, boolean includeAll) {
    PsiClass base = findClass(module, name);
    if (base != null) {
      GlobalSearchScope scope = includeAll ?
                                GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false) :
                                GlobalSearchScope.moduleScope(module);
      return ClassInheritorsSearch.search(base, scope, true).findAll();
    }
    return Collections.emptyList();
  }

  @Nullable
  public static PsiClass findClass(Module module, @Nullable String name) {
    if (name == null) {
      return null;
    }
    Project project = module.getProject();
    return JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myComponent;
  }

  public String getClassName() {
    return myResultClassName;
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    PsiClass psiClass = (PsiClass)myList.getSelectedValue();
    setOKActionEnabled(psiClass != null);
    myResultClassName = psiClass == null ? null : psiClass.getQualifiedName();
  }
}
