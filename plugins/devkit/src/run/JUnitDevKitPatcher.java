package org.jetbrains.idea.devkit.run;

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.projectRoots.ProjectJdk;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;

import java.io.File;

/**
 * User: anna
 * Date: Mar 4, 2005
 */
public class JUnitDevKitPatcher extends JUnitPatcher{

  public void patchJavaParameters(JavaParameters javaParameters) {
    final ProjectJdk jdk = javaParameters.getJdk();
    if (!(jdk.getSdkType() instanceof IdeaJdk)) {
      return;
    }
    String libPath = jdk.getHomePath() + File.separator + "lib";
    javaParameters.getVMParametersList().add("-Xbootclasspath/p:" + libPath + File.separator + "boot.jar");
    javaParameters.getClassPath().addFirst(libPath + File.separator + "idea.jar");
    javaParameters.getClassPath().addTail(libPath + File.separator + "idea_rt.jar");
    javaParameters.getClassPath().addFirst(libPath + File.separator + "resources.jar");
  }
}
