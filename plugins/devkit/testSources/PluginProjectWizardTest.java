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
package org.jetbrains.idea.devkit;

import com.intellij.ide.projectWizard.ProjectWizardTestCase;
import com.intellij.openapi.module.JavaModuleType;

/**
 * @author Dmitry Avdeev
 *         Date: 11/8/12
 */
public class PluginProjectWizardTest extends ProjectWizardTestCase {

  public void testPluginProject() throws Exception {
    createProjectFromTemplate(JavaModuleType.JAVA_GROUP, "IntelliJ IDEA Plugin", null);
  }
}
