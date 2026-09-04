// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.jetbrains.plugins.groovy.util.LightProjectTest;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

public class UnnecessaryQualifiedReferenceInspectionTest extends LightProjectTest implements HighlightingTest {
  @Test
  public void attributeExpression() {
    highlightingTest("""
                       class A { static foo }
                       A.@foo
                       A.@foo()
                       """);
  }
  
  @Test
  public void testStaticInterfaceMembers() {
    highlightingTest("""
                       interface Capibration {
                           int PI = 3
                           static void x() {
                               println <warning descr="Unnecessary qualified reference">Capibration</warning>.PI
                               <warning descr="Unnecessary qualified reference">Capibration</warning>.y()
                           }
                       
                           static void y() {}
                       }
                       """);
  }

  @Override
  public final @NotNull Collection<Class<? extends LocalInspectionTool>> getInspections() {
    return List.of(UnnecessaryQualifiedReferenceInspection.class);
  }

  @Override
  public LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_6_0;
  }
}
