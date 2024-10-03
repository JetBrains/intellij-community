// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.codeInspection.control.finalVar.GrFinalVariableAccessInspection;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.Test;

public class AutoFinalSupportTest extends GroovyLatestTest implements HighlightingTest {
    private void doTest(String text) {
        String newText = text;
        if (text.contains("AutoFinal")) {
            newText = "import groovy.transform.AutoFinal\n" + newText;
        }

        if (text.contains("CompileStatic")) {
            newText = "import groovy.transform.CompileStatic\n" + newText;
        }

        highlightingTest(newText, GrFinalVariableAccessInspection.class);
    }

    @Test
    public void autoFinalWorksOnFields() {
        doTest("""
                 @AutoFinal
                 @CompileStatic
                 class Bar {
                     String s = ""

                     def foo() {
                         <error>s</error> = ""
                     }
                 }
                 """);
    }

    @Test
    public void autoFinalWorksOnParameters() {
        doTest("""
                 @AutoFinal
                 @CompileStatic
                 class Bar {
                 
                     def foo(p) {
                         <error>p</error> = 1
                     }
                 }
                 """);
    }

    @Test
    public void autoFinalWorksOnNestedClasses() {
        doTest("""
                 @AutoFinal
                 @CompileStatic
                 class Foo {
                 
                     static class Bar {
                         String s = ""

                         def foo() {
                             <error>s</error> = ""
                         }
                     }
                 
                 }
                 """);
    }

    @Test
    public void autoFinalWorksForClosureParameters() {
        doTest("""
                 @AutoFinal
                 @CompileStatic
                 class Bar {
                 
                     def foo() {
                         1.with {a -> <warning>a</warning> = 2}
                     }
                 }
                 """);
    }

    @Test
    public void disabledAutoFinal() {
        doTest("""
                 @AutoFinal
                 @CompileStatic
                 class Bar {

                     @AutoFinal(enabled = false)
                     def foo(r) {
                         r = 2
                     }
                 }""");
    }
}
