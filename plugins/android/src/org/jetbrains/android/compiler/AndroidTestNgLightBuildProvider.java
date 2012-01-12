package org.jetbrains.android.compiler;

import com.intellij.execution.configurations.RunConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTestNgLightBuildProvider implements AndroidLightBuildProvider {
  @Override
  public boolean toPerformLightBuild(@NotNull RunConfiguration runConfiguration) {
    return runConfiguration instanceof TestNGConfiguration;
  }
}
