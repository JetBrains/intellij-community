/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 15-Jun-2010
 */
package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

import java.util.Map;
import java.util.Set;

public class ExcludeFromRunAction extends AnAction{
  private static final Logger LOG = Logger.getInstance("#" + ExcludeFromRunAction.class.getName());

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    LOG.assertTrue(project != null);
    final JUnitConfiguration configuration = (JUnitConfiguration)RuntimeConfiguration.DATA_KEY.getData(dataContext);
    LOG.assertTrue(configuration != null);
    final Set<String> patterns = configuration.getPersistentData().getPatterns();
    final AbstractTestProxy testProxy = AbstractTestProxy.DATA_KEY.getData(dataContext);
    LOG.assertTrue(testProxy != null);
    patterns.remove(((PsiClass)testProxy.getLocation(project).getPsiElement()).getQualifiedName());
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(false);
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final RuntimeConfiguration configuration = RuntimeConfiguration.DATA_KEY.getData(dataContext);
      if (configuration instanceof JUnitConfiguration) {
        final JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
        if (data.TEST_OBJECT == JUnitConfiguration.TEST_PATTERN) {
          final AbstractTestProxy testProxy = AbstractTestProxy.DATA_KEY.getData(dataContext);
          if (testProxy != null) {
            final Location location = testProxy.getLocation(project);
            if (location != null) {
              final PsiElement psiElement = location.getPsiElement();
              if (psiElement instanceof PsiClass && data.getPatterns().contains(((PsiClass)psiElement).getQualifiedName())) {
                presentation.setVisible(true);
              }
            }
          }
        }
      }
    }
  }
}