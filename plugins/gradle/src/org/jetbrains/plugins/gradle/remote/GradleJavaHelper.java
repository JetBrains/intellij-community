package org.jetbrains.plugins.gradle.remote;

import com.intellij.openapi.projectRoots.JdkUtil;
import org.jetbrains.annotations.Nullable;

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
  public String getJdkHome() {
    List<String> candidates = new ArrayList<String>();
    candidates.add(System.getProperty(GRADLE_JAVA_HOME_KEY));
    candidates.add(System.getenv("JAVA_HOME"));
    candidates.add(System.getProperty("java.home"));
    for (String candidate : candidates) {
      if (candidate != null && JdkUtil.checkForJre(candidate)) {
        return candidate;
      }
    }
    return null;
  }
}
