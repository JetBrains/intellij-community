// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

/**
 * @author Max Medvedev
 */
public class GrMethodMayBeStaticTest extends GrMethodMayBeStaticTestBase {
  public void testOldMain() {
    doTest("""
             class A {
                 void <warning descr="Method may be static">main</warning>() {
                   println "Hello world!"
                 }
             }
             """);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
