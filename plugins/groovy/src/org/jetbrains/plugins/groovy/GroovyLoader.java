/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.compiler.GroovyCompiler;
import org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.debugger.GroovyPositionManager;
import org.jetbrains.plugins.groovy.findUsages.AccessorReferencesSearcher;
import org.jetbrains.plugins.groovy.findUsages.LateBoundReferencesSearcher;
import org.jetbrains.plugins.groovy.findUsages.MethodLateBoundReferencesSearcher;
import org.jetbrains.plugins.groovy.findUsages.PropertyReferencesSearcher;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionData;
import org.jetbrains.plugins.groovy.lang.editor.GroovyQuoteHandler;
import org.jetbrains.plugins.groovy.refactoring.GroovyClassMoveCallback;

import java.util.HashSet;
import java.util.Set;

/**
 * Main application component, that loads Groovy language support
 *
 * @author ilyas
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

  private static void loadGroovy() {
    ApplicationManager.getApplication().runWriteAction(
        new Runnable() {
          public void run() {
            FileTypeManager.getInstance().registerFileType(GroovyFileType.GROOVY_FILE_TYPE, GROOVY_EXTENTIONS.toArray(new String[GROOVY_EXTENTIONS.size()]));
          }
        }
    );

    CompletionUtil.registerCompletionData(GroovyFileType.GROOVY_FILE_TYPE,
        new GroovyCompletionData());

    MethodReferencesSearch.INSTANCE.registerExecutor(new AccessorReferencesSearcher());
    MethodReferencesSearch.INSTANCE.registerExecutor(new MethodLateBoundReferencesSearcher());

    ReferencesSearch.INSTANCE.registerExecutor(new PropertyReferencesSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new LateBoundReferencesSearcher());

    TypedHandler.registerQuoteHandler(GroovyFileType.GROOVY_FILE_TYPE, new GroovyQuoteHandler());

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(Project project) {
        CompilerManager compilerManager = CompilerManager.getInstance(project);
        GroovyToJavaGenerator generator = new GroovyToJavaGenerator(project);
        compilerManager.addCompiler(generator);
        compilerManager.addCompilationStatusListener(generator);

        compilerManager.addCompiler(new GroovyCompiler(project));
        compilerManager.addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);

        DebuggerManager.getInstance(project).registerPositionManagerFactory(new Function<DebugProcess, PositionManager>() {
          public PositionManager fun(DebugProcess debugProcess) {
            return new GroovyPositionManager(debugProcess);
          }
        });

        RefactoringListenerManager.getInstance(project).addListenerProvider(new GroovyClassMoveCallback());
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