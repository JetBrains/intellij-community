package com.intellij.execution.junit;

import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.psi.PsiClass;
import com.intellij.testIntegration.JavaTestFrameworkDescriptor;
import org.jetbrains.annotations.Nullable;

public class JUnit4FrameworkDescriptor extends JavaTestFrameworkDescriptor {
  public String getName() {
    return "JUnit4";
  }

  protected String getMarkerClassFQName() {
    return getTestAnnotation();
  }

  public String getLibraryPath() {
    return JavaSdkUtil.getJunit4JarPath();
  }

  @Nullable
  public String getDefaultSuperClass() {
    return null;
  }

  @Nullable
  public String getSetUpAnnotation() {
    return "org.junit.Before";
  }

  @Nullable
  public String getTearDownAnnotation() {
    return "org.junit.After";
  }

  @Nullable
  public String getTestAnnotation() {
    return "org.junit.Test";
  }

  public boolean isTestClass(PsiClass clazz) {
    return JUnitUtil.isJUnit4TestClass(clazz);
  }
}