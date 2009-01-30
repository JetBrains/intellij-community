package com.intellij.execution.junit;

import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.psi.PsiClass;
import com.intellij.testIntegration.JavaTestFrameworkDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import org.jetbrains.annotations.Nullable;

public class JUnit4FrameworkDescriptor extends JavaTestFrameworkDescriptor {
  public String getName() {
    return "JUnit4";
  }

  protected String getMarkerClassFQName() {
    return "org.junit.Test";
  }

  public String getLibraryPath() {
    return JavaSdkUtil.getJunit4JarPath();
  }

  @Nullable
  public String getDefaultSuperClass() {
    return null;
  }

  public boolean isTestClass(PsiClass clazz) {
    return JUnitUtil.isJUnit4TestClass(clazz);
  }

  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 SetUp Method.java");
  }

  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 TearDown Method.java");
  }

  public FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 Test Method.java");
  }
}