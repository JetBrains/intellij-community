/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import org.jetbrains.plugins.groovy.debugger.GroovyPositionManager;
import org.jetbrains.plugins.groovy.lang.GroovyChangeUtilSupport;

/**
 * Main application component, that loads Groovy language support
 *
 * @author ilyas
 */
public class GroovyLoader {

  public GroovyLoader() {
    ChangeUtil.registerCopyHandler(new GroovyChangeUtilSupport());

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(final Project project) {
        GroovyPositionManager.registerPositionManager(project);
      }
    });
  }

}
