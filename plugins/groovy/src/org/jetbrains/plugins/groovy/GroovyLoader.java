/*
 *  Copyright 2000-2007 JetBrains s.r.o.
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
 *
 */

package org.jetbrains.plugins.groovy;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.compiler.CompilationUnitsFactory;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerProcess;
//import org.jetbrains.plugins.groovy.compiler.GroovyCompiler;

import java.util.HashSet;
import java.util.Set;

/**
 * Main application component, that loads Groovy language support
 *
 * @author Ilya.Sergey
 */
public class GroovyLoader implements ApplicationComponent {

  @NotNull
  public static final String GROOVY_EXTENTION = "groovy";

  @NotNull
  public static final String GVY_EXTENTION = "gvy";

  @NotNull
  public static final String GY_EXTENTION = "gy";

  @NotNull
  public static final String GROOVY_SCRIPT_EXTENTION = "gsh";

  @NotNull
  public static final Set<String> GROOVY_EXTENTIONS = new HashSet<String>();

  static {
    GROOVY_EXTENTIONS.add(GROOVY_EXTENTION);
    GROOVY_EXTENTIONS.add(GVY_EXTENTION);
    GROOVY_EXTENTIONS.add(GY_EXTENTION);
    GROOVY_EXTENTIONS.add(GROOVY_SCRIPT_EXTENTION);
  }

  public GroovyLoader() {
  }

  public void initComponent() {
    loadGroovy();
  }

  public static void loadGroovy() {
    ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                FileTypeManager.getInstance().registerFileType(GroovyFileType.GROOVY_FILE_TYPE, GROOVY_EXTENTIONS.toArray(new String[GROOVY_EXTENTIONS.size()]));
              }
            }
    );

/*
    CompletionUtil.registerCompletionData(ScalaFileType.SCALA_FILE_TYPE,
            ScalaToolsFactory.getInstance().createScalaCompletionData());
*/


//    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
//      public void projectOpened(Project project) {
//        CompilerManager compilerManager = CompilerManager.getInstance(project);
//        compilerManager.addCompiler(new GroovyCompiler(new CompilationUnitsFactory()));
//        compilerManager.addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);
//      }
//    });
//
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(Project project) {
        CompilerManager compilerManager = CompilerManager.getInstance(project);
        compilerManager.addCompiler(new GroovyCompilerProcess(new CompilationUnitsFactory()));
        compilerManager.addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);
      }
    });
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "groovy.support.loader";
  }
}