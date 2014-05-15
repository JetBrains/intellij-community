/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.doc.actions;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiPackage;
import com.intellij.util.IconUtil;
import org.jetbrains.plugins.groovy.doc.GroovyDocConfiguration;

import javax.swing.*;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.10.2008
 */
public class GroovyDocAddPackageAction extends AnAction implements DumbAware {
  private final DefaultListModel myDataModel;

  public GroovyDocAddPackageAction(final DefaultListModel dataModel) {
    super("Add package", "Add package", IconUtil.getAddIcon());
    myDataModel = dataModel;
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());

    PackageChooserDialog chooser = new PackageChooserDialog("Choose packages", project);
    chooser.show();

    final List<PsiPackage> packages = chooser.getSelectedPackages();

    for (PsiPackage aPackage : packages) {
      final String qualifiedName = aPackage.getQualifiedName();

      if (qualifiedName.isEmpty()){
        myDataModel.addElement(GroovyDocConfiguration.ALL_PACKAGES);
      }
      myDataModel.addElement(qualifiedName);
    }
  }
}
