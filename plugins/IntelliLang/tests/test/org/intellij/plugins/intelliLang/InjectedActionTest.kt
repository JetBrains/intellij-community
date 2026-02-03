// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class InjectedActionTest : JavaCodeInsightFixtureTestCase() {
  fun testCallCopyReference() {
    // language="JAVA"
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class AB {
        @org.intellij.lang.annotations.Language("Java") String ac = ""${'"'}
        class A {
          void ab(){
             if(1==2.<caret>){}
          }<caret>
        }""${'"'};
      }
      """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_COPY_REFERENCE)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      class AB {
        @org.intellij.lang.annotations.Language("Java") String ac = ""${'"'}
        class A {
          void ab(){
             if(1==2.aaa.java:4){}
          }aaa.java:4
        }""${'"'};
      }
      """.trimIndent())
  }
}
