// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.dgm;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase;
import org.junit.Assert;

public class ResolveDGMMethodLatestTest extends GroovyResolveTestCase {

  @NotNull
  private LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;

  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  public void setProjectDescriptor(@NotNull LightProjectDescriptor projectDescriptor) {
    this.projectDescriptor = projectDescriptor;
  }

  public void testCloseable() {
    myFixture.addClass("package java.io; class Closeable {}");
    GrGdkMethod resolved = resolveByText(
      """
        class A implements Closeable {}
        new A().withC<caret>loseable {}
        """, GrGdkMethod.class);
    Assert.assertEquals("org.codehaus.groovy.runtime.IOGroovyMethods",
                        resolved.getStaticMethod().getContainingClass().getQualifiedName());
  }
}
