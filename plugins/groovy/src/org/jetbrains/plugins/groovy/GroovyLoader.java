package org.jetbrains.plugins.groovy;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.codeInsight.completion.CompletionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.compiler.GroovyCompiler;
import org.jetbrains.plugins.groovy.compiler.CompilationUnitsFactory;

/**
 * Main application component, that loads Groovy language support
 *
 * @author Ilya.Sergey
 */
public class GroovyLoader implements ApplicationComponent {
  public GroovyLoader() {
  }

  public void initComponent() {
    loadGroovy();
  }

  public static void loadGroovy() {
    ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                FileTypeManager.getInstance().registerFileType(GroovyFileType.GROOVY_FILE_TYPE, new String[]{"groovy", "gvy", "gy", "gsh"});
              }
            }
    );

/*
    CompletionUtil.registerCompletionData(ScalaFileType.SCALA_FILE_TYPE,
            ScalaToolsFactory.getInstance().createScalaCompletionData());
*/

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(Project project) {
        CompilerManager compilerManager = CompilerManager.getInstance(project);
        compilerManager.addCompiler(new GroovyCompiler(new CompilationUnitsFactory()));
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