package org.jetbrains.javafx.build;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.JavaFxFileType;

import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxCompilerLoader extends AbstractProjectComponent {
  protected JavaFxCompilerLoader(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    compilerManager.addTranslatingCompiler(new JavaFxCompiler(),
                                           new HashSet<FileType>(Arrays.asList(JavaFxFileType.INSTANCE, StdFileTypes.CLASS)),
                                           new HashSet<FileType>(Arrays.asList(StdFileTypes.CLASS)));
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "JavaFxCompilerLoader";
  }
}
