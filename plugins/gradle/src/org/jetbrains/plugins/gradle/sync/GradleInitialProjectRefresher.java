package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * Refreshes linked gradle project on IJ project opening.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/13/12 12:01 PM
 */
public class GradleInitialProjectRefresher implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    GradleUtil.refreshProject(project);
  }
}
