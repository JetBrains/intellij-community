package org.jetbrains.plugins.gradle.remote;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates functionality of deciding what java should be used by the gradle process.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/27/12 2:20 PM
 */
public class GradleJavaHelper {

  public static final String GRADLE_JAVA_HOME_KEY = "gradle.java.home";
  
  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public String getJdkHome(@Nullable Project project) {
    List<String> candidates = ContainerUtilRt.newArrayList();
    candidates.add(System.getProperty(GRADLE_JAVA_HOME_KEY));
    candidates.add(System.getenv("JAVA_HOME"));
    for (String candidate : candidates) {
      if (candidate != null && JdkUtil.checkForJdk(new File(candidate))) {
        return candidate;
      }
    }

    if (project != null) {
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk != null) {
        String path = sdk.getHomePath();
        if (path != null && JdkUtil.checkForJdk(new File(path))) {
          return path;
        }
      }
    }

    Sdk[] sdks = ProjectJdkTable.getInstance().getAllJdks();
    if (sdks != null) {
      for (Sdk sdk : sdks) {
        String path = sdk.getHomePath();
        if (path != null && JdkUtil.checkForJdk(new File(path))) {
          return path;
        }
      }
    }
    
    return null;
  }
}
