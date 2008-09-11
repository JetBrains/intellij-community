package com.intellij.execution.junit;

import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.psi.PsiClass;
import com.intellij.testIntegration.JavaTestFrameworkDescriptor;
import org.jetbrains.annotations.Nullable;

public class JUnit3FrameworkDescriptor extends JavaTestFrameworkDescriptor {
  public String getName() {
    return "JUnit3";
  }

  protected String getMarkerClassFQName() {
    return getDefaultSuperClass();
  }

  public String getLibraryPath() {
    return JavaSdkUtil.getJunit3JarPath();
  }

  @Nullable
  public String getDefaultSuperClass() {
    return "junit.framework.TestCase";
  }

  @Nullable
  public String getSetUpAnnotation() {
    return null;
  }

  @Nullable
  public String getTearDownAnnotation() {
    return null;
  }

  @Nullable
  public String getTestAnnotation() {
    return null;
  }

  public boolean isTestClass(PsiClass clazz) {
    return JUnitUtil.isJUnit3TestClass(clazz);
  }
}
