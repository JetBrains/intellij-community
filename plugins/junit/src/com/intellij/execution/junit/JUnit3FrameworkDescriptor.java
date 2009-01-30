package com.intellij.execution.junit;

import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.psi.PsiClass;
import com.intellij.testIntegration.JavaTestFrameworkDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import org.jetbrains.annotations.Nullable;

public class JUnit3FrameworkDescriptor extends JavaTestFrameworkDescriptor {
  public String getName() {
    return "JUnit3";
  }

  protected String getMarkerClassFQName() {
    return "junit.framework.TestCase";
  }

  public String getLibraryPath() {
    return JavaSdkUtil.getJunit3JarPath();
  }

  @Nullable
  public String getDefaultSuperClass() {
    return "junit.framework.TestCase";
  }

  public boolean isTestClass(PsiClass clazz) {
    return JUnitUtil.isJUnit3TestClass(clazz);
  }

  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit3 SetUp Method.java");
  }

  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit3 TearDown Method.java");
  }

  public FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit3 Test Method.java");
  }
}
