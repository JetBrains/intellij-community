/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.griffon;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.Icons;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.mvc.MvcIcons;
import org.jetbrains.plugins.groovy.mvc.projectView.*;

import javax.swing.*;
import java.util.List;

/**
 * @author peter
 */
public class GriffonToolWindowFactory extends MvcToolWindowDescriptor {
  public GriffonToolWindowFactory() {
    super(GriffonFramework.getInstance());
  }

  @Override
  public void fillModuleChildren(List<AbstractTreeNode> result, Module module, ViewSettings viewSettings, VirtualFile root) {
    final Project project = module.getProject();
    final PsiDirectory domains = findDirectory(project, root, "griffon-app/models");
    if (domains != null) {
      result.add(new TopLevelDirectoryNode(module, domains, viewSettings, "Model classes", MvcIcons.DOMAIN_CLASSES_FOLDER, AbstractMvcPsiNodeDescriptor.DOMAIN_CLASSES_FOLDER) {
        @Override
        protected AbstractTreeNode createClassNode(final GrTypeDefinition typeDefinition) {
          return new DomainClassNode(getModule(), typeDefinition, getSettings());
        }
      });
    }

    final PsiDirectory conf = findDirectory(project, root, "griffon-app/conf");
    if (conf != null) {
      result.add(new TopLevelDirectoryNode(module, conf, viewSettings, "Configuration", MvcIcons.CONFIG_FOLDER, AbstractMvcPsiNodeDescriptor.CONFIG_FOLDER));
    }

    final PsiDirectory controllers = findDirectory(project, root, "griffon-app/controllers");
    if (controllers != null) {
      result.add(new TopLevelDirectoryNode(module, controllers, viewSettings, "Controllers", MvcIcons.CONTROLLERS_FOLDER, AbstractMvcPsiNodeDescriptor.CONTROLLERS_FOLDER));
    }

    final PsiDirectory views = findDirectory(project, root, "griffon-app/views");
    if (views != null) {
      result.add(new TopLevelDirectoryNode(module, views, viewSettings, "Views", GroovyIcons.GROOVY_ICON_16x16, AbstractMvcPsiNodeDescriptor.VIEWS_FOLDER));
    }

    final PsiDirectory srcMain = findDirectory(project, root, "src/main");
    if (srcMain != null) {
      result.add(new TopLevelDirectoryNode(module, srcMain, viewSettings, "Project Sources", GroovyIcons.GROOVY_ICON_16x16, AbstractMvcPsiNodeDescriptor.SRC_FOLDERS));
    }

    final PsiDirectory testsUnit = findDirectory(project, root, "test/unit");
    if (testsUnit != null) {
      result.add(new TestsTopLevelDirectoryNode(module, testsUnit, viewSettings, "Unit Tests", Icons.TEST_SOURCE_FOLDER,
                                                Icons.TEST_SOURCE_FOLDER));
    }

    final PsiDirectory testsIntegration = findDirectory(project, root, "test/integration");
    if (testsIntegration != null) {
      result.add(new TestsTopLevelDirectoryNode(module, testsIntegration, viewSettings, "Integration Tests", Icons.TEST_SOURCE_FOLDER,
                                                Icons.TEST_SOURCE_FOLDER));
    }
  }

  @Override
  public Icon getModuleNodeIcon() {
    return GriffonFramework.GRIFFON_ICON;
  }


}
