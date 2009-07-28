package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator;

import java.util.Arrays;
import java.util.HashSet;

/**
 * @author ilyas
 */
public class GroovyCompilerLoader implements ProjectComponent{
  private final Project myProject;

  public GroovyCompilerLoader(Project project) {
    myProject = project;
  }

  public void projectOpened() {
    HashSet<FileType> inputSet = new HashSet<FileType>(Arrays.asList(GroovyFileType.GROOVY_FILE_TYPE, StdFileTypes.JAVA));
    HashSet<FileType> outputSet = new HashSet<FileType>(Arrays.asList(StdFileTypes.CLASS));
    CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    compilerManager.addTranslatingCompiler(new GroovyCompiler(myProject), inputSet, outputSet);

    GroovyToJavaGenerator generator = new GroovyToJavaGenerator(myProject);
    compilerManager.addCompiler(generator);
    compilerManager.addCompilationStatusListener(generator);
    compilerManager.addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);

    /*compilerManager.addTranslatingCompiler(new NewGroovyToJavaGenerator(myProject),
                                           new HashSet<FileType>(Arrays.asList(GroovyFileType.GROOVY_FILE_TYPE, StdFileTypes.JAVA)),
                                           new HashSet<FileType>(Arrays.asList(StdFileTypes.JAVA)));*/
  }

  public void projectClosed() {

  }

  @NotNull
  public String getComponentName() {
    return "GroovyCompilerLoader";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
