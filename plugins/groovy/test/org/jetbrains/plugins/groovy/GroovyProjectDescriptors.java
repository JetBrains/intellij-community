// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy;

import com.intellij.testFramework.LightProjectDescriptor;

public interface GroovyProjectDescriptors {

  LightProjectDescriptor GROOVY_2_1 = new RepositoryProjectDescriptor("org.codehaus.groovy:groovy-all:2.1.0");
  LightProjectDescriptor GROOVY_2_2 = new RepositoryProjectDescriptor("org.codehaus.groovy:groovy:2.2.0");
  LightProjectDescriptor GROOVY_2_3 = new RepositoryProjectDescriptor("org.codehaus.groovy:groovy:2.3.0");
  LightProjectDescriptor GROOVY_2_5 = new RepositoryProjectDescriptor("org.codehaus.groovy:groovy:2.5.0");
  LightProjectDescriptor GROOVY_3_0 = new RepositoryProjectDescriptor("org.codehaus.groovy:groovy:3.0.0-alpha-2");
}
