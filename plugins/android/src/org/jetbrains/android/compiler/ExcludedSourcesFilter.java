package org.jetbrains.android.compiler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
* @author Eugene.Kudelevsky
*/
class ExcludedSourcesFilter implements Condition<File> {
  private final Project myProject;

  public ExcludedSourcesFilter(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean value(File file) {
    return !AndroidCompileUtil.isExcludedFromCompilation(file, myProject);
  }
}
