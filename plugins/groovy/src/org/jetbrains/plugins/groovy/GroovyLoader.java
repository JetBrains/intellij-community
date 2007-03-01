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

/**
 * Main application component, that loads Groovy language support
 *
 * @author Ilya.Sergey
 */
public class GroovyLoader implements ApplicationComponent {
  public GroovyLoader() {}

  public void initComponent() {
    loadGroovy();
  }

  public static void loadGroovy() {
    ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                FileTypeManager.getInstance().registerFileType(GroovyFileType.GROOVY_FILE_TYPE, "groovy");
              }
            }
    );


/*
    CompletionUtil.registerCompletionData(ScalaFileType.SCALA_FILE_TYPE,
            ScalaToolsFactory.getInstance().createScalaCompletionData());
*/

/*
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(Project project) {
        CompilerManager compilerManager = CompilerManager.getInstance(project);
        compilerManager.addCompiler(new ScalaCompiler(project));
        compilerManager.addCompilableFileType(ScalaFileType.SCALA_FILE_TYPE);
      }
    });
*/


  }

  public void disposeComponent() {}

  @NotNull
  public String getComponentName() {
    return "Groovy Loader";
  }
}