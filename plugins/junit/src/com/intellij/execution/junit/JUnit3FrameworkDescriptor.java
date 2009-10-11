/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
