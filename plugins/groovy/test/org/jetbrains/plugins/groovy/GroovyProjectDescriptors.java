// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;

public interface GroovyProjectDescriptors {

  TestLibrary LIB_GROOVY_1_6 = new RepositoryTestLibrary("org.codehaus.groovy:groovy:1.6.9");
  TestLibrary LIB_GROOVY_1_7 = new RepositoryTestLibrary("org.codehaus.groovy:groovy:1.7.11");
  TestLibrary LIB_GROOVY_2_1 = new RepositoryTestLibrary("org.codehaus.groovy:groovy-all:2.1.0");
  TestLibrary LIB_GROOVY_2_2 = new RepositoryTestLibrary("org.codehaus.groovy:groovy:2.2.0");
  TestLibrary LIB_GROOVY_2_3 = new RepositoryTestLibrary("org.codehaus.groovy:groovy:2.3.0");
  TestLibrary LIB_GROOVY_2_4 = new RepositoryTestLibrary("org.codehaus.groovy:groovy-all:2.4.17");
  TestLibrary LIB_GROOVY_2_5 = new RepositoryTestLibrary("org.codehaus.groovy:groovy:2.5.6");
  TestLibrary LIB_GROOVY_3_0 = new RepositoryTestLibrary("org.codehaus.groovy:groovy:3.0.0-alpha-2");

  LightProjectDescriptor GROOVY_1_6 = new LibraryLightProjectDescriptor(LIB_GROOVY_1_6);
  LightProjectDescriptor GROOVY_1_7 = new LibraryLightProjectDescriptor(LIB_GROOVY_1_7);
  LightProjectDescriptor GROOVY_2_1 = new LibraryLightProjectDescriptor(LIB_GROOVY_2_1);
  LightProjectDescriptor GROOVY_2_2 = new LibraryLightProjectDescriptor(LIB_GROOVY_2_2);
  LightProjectDescriptor GROOVY_2_3 = new LibraryLightProjectDescriptor(LIB_GROOVY_2_3);
  LightProjectDescriptor GROOVY_2_5 = new LibraryLightProjectDescriptor(LIB_GROOVY_2_5);
  LightProjectDescriptor GROOVY_3_0 = new LibraryLightProjectDescriptor(LIB_GROOVY_3_0);

  TestLibrary LIB_GROOVY_LATEST = LIB_GROOVY_2_4;
  LightProjectDescriptor GROOVY_LATEST = new LibraryLightProjectDescriptor(LIB_GROOVY_LATEST);
  LightProjectDescriptor GROOVY_LATEST_REAL_JDK = new LibraryLightProjectDescriptor(LIB_GROOVY_LATEST) {
    @Override
    public Sdk getSdk() {
      return JavaSdk.getInstance().createJdk("TEST_JDK", IdeaTestUtil.requireRealJdkHome(), false);
    }
  };
  LightProjectDescriptor GROOVY_3_0_REAL_JDK = new LibraryLightProjectDescriptor(LIB_GROOVY_3_0) {
    @Override
    public Sdk getSdk() {
      return JavaSdk.getInstance().createJdk("TEST_JDK", IdeaTestUtil.requireRealJdkHome(), false);
    }
  };
}
