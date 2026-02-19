// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.confusing.ClashingTraitMethodsInspection;

/**
 * Created by Max Medvedev on 09/06/14
 */
public class ClashingTraitMethodsTest extends GrHighlightingTestBase {
  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new ClashingTraitMethodsInspection[]{new ClashingTraitMethodsInspection()};
  }

  public void testClash() {
    doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         trait T2 {
                           def foo(){}
                         }

                         class <warning descr="Traits T1, T2 contain clashing methods with signature foo()">A</warning> implements T1, T2 {

                         }
                         """);
  }

  public void testCustomImplementationNoClash() {
    doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         trait T2 {
                           def foo(){}
                         }

                         class A implements T1, T2 {
                             def foo() {}
                         }
                         """);
  }

  public void testNoClash() {
    doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         trait T2 {
                           abstract def foo()
                         }

                         class A implements T1, T2 {
                         }
                         """);
  }

  public void testNoClashWithInterface() {
    doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         interface T2 {
                           def foo()
                         }

                         class A implements T1, T2 {
                         }
                         """);
  }

  public void testClashWithDefaultMethodInterfaces() {
    doTestHighlighting("""
                         interface T1 {
                           default foo(){}
                         }

                         interface T2 {
                           default foo() {}
                         }

                         class <warning descr="Traits T1, T2 contain clashing methods with signature foo()">A</warning> implements T1, T2 {
                         }
                         """);
  }

  public void testClashTraitWithDefaultMethodInterface() {
    doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         interface T2 {
                           default foo() {}
                         }

                         class <warning descr="Traits T1, T2 contain clashing methods with signature foo()">A</warning> implements T1, T2 {
                         }
                         """);
  }

  public void testNoClashInInheritor() {
    doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         interface T2 {
                           def foo()
                         }

                         class A implements T1, T2 {
                         }

                         class B extends A{}
                         """);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_3_0;
  }
}
