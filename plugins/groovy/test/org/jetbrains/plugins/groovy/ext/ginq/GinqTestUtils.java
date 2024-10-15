// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor;
import org.jetbrains.plugins.groovy.RepositoryTestLibrary;

public final class GinqTestUtils {
  private static final RepositoryTestLibrary LIB_GINQ = new RepositoryTestLibrary("org.apache.groovy:groovy-ginq:4.0.0");
  private static final LightProjectDescriptor PROJECT_DESCRIPTOR = new LibraryLightProjectDescriptor(
    GroovyProjectDescriptors.LIB_GROOVY_4_0.plus(LIB_GINQ)) {
    @Override
    public Sdk getSdk() {
      return JavaSdk.getInstance().createJdk("TEST_JDK", IdeaTestUtil.requireRealJdkHome(), false);
    }
  };

  public static LightProjectDescriptor getProjectDescriptor() {
    return PROJECT_DESCRIPTOR;
  }
}