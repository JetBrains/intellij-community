package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.compiler.generator.GroovycStubGenerator;

import java.util.Arrays;
import java.util.HashSet;

/**
 * @author ilyas
 */
public class GroovyCompilerLoader extends AbstractProjectComponent {

  public GroovyCompilerLoader(Project project) {
    super(project);
  }

  public void projectOpened() {
    CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    compilerManager.addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);

    if (System.getProperty("use.groovyc.stub.generator", "false").equals("true")) {
      compilerManager.addTranslatingCompiler(new GroovycStubGenerator(myProject), 
                                             new HashSet<FileType>(Arrays.asList(GroovyFileType.GROOVY_FILE_TYPE, StdFileTypes.JAVA)),
                                             new HashSet<FileType>(Arrays.asList(StdFileTypes.CLASS)));
    } else {
      GroovyToJavaGenerator generator = new GroovyToJavaGenerator(myProject);
      compilerManager.addCompiler(generator);
      compilerManager.addCompilationStatusListener(generator);
    }

    compilerManager.addTranslatingCompiler(new GroovyCompiler(myProject),
                                           new HashSet<FileType>(Arrays.asList(GroovyFileType.GROOVY_FILE_TYPE, StdFileTypes.CLASS)),
                                           new HashSet<FileType>(Arrays.asList(StdFileTypes.CLASS)));
  }

  @NotNull
  public String getComponentName() {
    return "GroovyCompilerLoader";
  }
}
