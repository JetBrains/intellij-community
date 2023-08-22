// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.codeInspection.control.finalVar.GrFinalVariableAccessInspection
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.junit.Test

@CompileStatic
class AutoFinalSupportTest extends GroovyLatestTest implements HighlightingTest {
    private void doTest(String text) {
        @Language("Groovy") String newText = text
        if (text.contains("AutoFinal")) {
            newText = "import groovy.transform.AutoFinal\n" + newText
        }
        if (text.contains("CompileStatic")) {
            newText = "import groovy.transform.CompileStatic\n" + newText
        }
        highlightingTest(newText, GrFinalVariableAccessInspection)
    }

    @Test
    void 'AutoFinal works on fields'() {
        doTest """
@AutoFinal
@CompileStatic
class Bar {
    String s = ""
    
    def foo() {
        <error>s</error> = ""
    }
}
"""
    }

    @Test
    void 'AutoFinal works on parameters'() {
        doTest """
@AutoFinal
@CompileStatic
class Bar {

    def foo(p) {
        <error>p</error> = 1
    }
}
"""
    }

    @Test
    void 'AutoFinal works on nested classes'() {
        doTest """
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
"""
    }

    @Test
    void 'AutoFinal works for closure parameters'() {
        doTest """
@AutoFinal
@CompileStatic
class Bar {

    def foo() {
        1.with {a -> <warning>a</warning> = 2}
    }
}
"""
    }

    @Test
    void 'disabled AutoFinal'() {
        doTest """
@AutoFinal
@CompileStatic
class Bar {
    
    @AutoFinal(enabled = false)
    def foo(r) {
        r = 2
    }
}"""
    }
}
