// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection
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
        highlightingTest(newText, GroovyConstructorNamedArgumentsInspection)
    }

    @Test
    void 'autoFinal works on fields'() {
        doTest """
@AutoFinal
@CompileStatic
class Bar {
    String s
    
    def foo() {
        <error>s</error> = ""
    }
}
"""
    }

    @Test
    void 'autoFinal works on parameters'() {
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
}
