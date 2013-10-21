/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle;

import com.intellij.ide.projectWizard.NewProjectWizardTestCase;
import com.intellij.ide.projectWizard.ProjectTypeStep;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;

/**
 * @author Dmitry Avdeev
 *         Date: 18.10.13
 */
public class GradleProjectWizardTest extends NewProjectWizardTestCase {

  public void testGradleProject() throws Exception {
    Project project = createProject(new Consumer<Step>() {
      @Override
      public void consume(Step step) {
        if (step instanceof ProjectTypeStep) {
          assertTrue(((ProjectTypeStep)step).setSelectedProjectType("Java", "Gradle"));
        }
      }
    });
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setupJdk();
  }
}
