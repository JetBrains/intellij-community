// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.codeInsight.completion.CompletionItemLookupElement
import com.intellij.codeInsight.completion.command.CommandCompletionLookupElement
import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.lookup.LookupElementCustomPreviewHolder
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateModCompletionItemProvider
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@NeedsIndex.SmartMode(reason = "it requires highlighting")
@TestApplication
class JavaCommandsCompletionInjectedTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21)
  }

  private val completion by lightCompletionFixture(projectFixture)
  private val myFixture get() = completion.fixture

  @BeforeEach
  fun setUp() {
    Registry.get("ide.completion.command.enabled").setValue(false, myFixture.testRootDisposable)
    Registry.get("ide.completion.command.force.enabled").setValue(true, myFixture.testRootDisposable)
  }

  @Test
  fun testFlip(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    // language="JAVA"
    @Suppress("ConstantValue", "StatementWithEmptyBody")
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class AB {
        @org.intellij.lang.annotations.Language("Java") String ac = ""${'"'}
        class A {
          void ab(){
             if(1==2.<caret>){}
          }
        }""${'"'};
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Flip", ignoreCase = true) })
    // language="JAVA"
    @Suppress("ConstantValue", "StatementWithEmptyBody")
    myFixture.checkResult("""
        class AB {
          @org.intellij.lang.annotations.Language("Java") String ac = ""${'"'}
          class A {
            void ab(){
               if(2 == 1){}
            }
          }""${'"'};
        }""".trimIndent())
  }

  @Test
  fun testFlipPreview(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    // language="JAVA"
    @Suppress("ConstantValue", "StatementWithEmptyBody")
    myFixture.configureByText(JavaFileType.INSTANCE, """
    class AB {
        @org.intellij.lang.annotations.Language("Java") String ac = ""${'"'}
        class A {
            void ab(){
                if(1==2..<caret>){}
            }
        }""${'"'};
    }      
    """.trimIndent())
    val elements = myFixture.completeBasic()
    val lookupElement = elements
      .firstOrNull { element -> element.lookupString.contains("Flip", ignoreCase = true) }
      ?.`as`(CommandCompletionLookupElement::class.java)
    assertNotNull(lookupElement)
    val preview = lookupElement?.preview(ActionContext.from(myFixture.editor, myFixture.file)) as? IntentionPreviewInfo.CustomDiff
    // language="JAVA"

    @Suppress("ConstantValue", "StatementWithEmptyBody")
    assertEquals("""
        class AB {
            @org.intellij.lang.annotations.Language("Java") String ac = ""${'"'}
            class A {
                void ab(){
                    if(2 == 1){}
                }
            }""${'"'};
        }      
    """.trimIndent(), preview?.modifiedText())
  }

  @Test
  fun testFlipPreviewTemporaryEnabled(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    // language="JAVA"
    @Suppress("ConstantValue", "StatementWithEmptyBody")
    val psiFile = myFixture.configureByText(JavaFileType.INSTANCE, """
    class AB {
        String ac = ""${'"'}
        class A {
            void ab(){
                if(1==2..<caret>){}
            }
        }""${'"'};
    }      
    """.trimIndent())
    val textBlock = PsiTreeUtil.getParentOfType(psiFile.findElementAt(myFixture.editor.caretModel.offset), PsiLanguageInjectionHost::class.java)
    val temporaryPlacesRegistry = TemporaryPlacesRegistry.getInstance(myFixture.project)
    temporaryPlacesRegistry.addHost(textBlock, InjectedLanguage.create(JavaLanguage.INSTANCE.id))
    val elements = myFixture.completeBasic()
    val lookupElement = elements
      .firstOrNull { element -> element.lookupString.contains("Flip", ignoreCase = true) }
      ?.`as`(CommandCompletionLookupElement::class.java)
    assertNotNull(lookupElement)
    val preview = lookupElement?.preview(ActionContext.from(myFixture.editor, myFixture.file)) as? IntentionPreviewInfo.CustomDiff
    // language="JAVA"
    @Suppress("ConstantValue", "StatementWithEmptyBody")
    assertEquals("""
        class AB {
            String ac = ""${'"'}
            class A {
                void ab(){
                    if(2 == 1){}
                }
            }""${'"'};
        }      
    """.trimIndent(), preview?.modifiedText())
  }

  @Test
  fun testCastPostfixPreview(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.testRootDisposable)
    // language="JAVA"
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Hello {
        @org.intellij.lang.annotations.Language("JAVA") String string = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                o.cast<caret>
            }
        }""${'"'};
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    val item = elements.first { element -> element.lookupString.contains("cast", ignoreCase = true) }
      .`as`(LookupElementCustomPreviewHolder::class.java)
    assertNotNull(item)
    val preview = item!!.preview(ActionContext.from(myFixture.editor, myFixture.file))
    assertTrue(preview is IntentionPreviewInfo.CustomDiff)
    assertEquals("""
      class Hello {
        @org.intellij.lang.annotations.Language("JAVA") String string = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                (() o)
            }
        }""${'"'};
      }
    """.trimIndent(), (preview as IntentionPreviewInfo.CustomDiff).modifiedText())
  }

  @Test
  fun testCastPostfixPreviewTemporaryEnabled(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    // language="JAVA"
    val psiFile = myFixture.configureByText(JavaFileType.INSTANCE, """
      class Hello {
        String aaa = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                o.cast<caret>
            }
        }""${'"'};
      }
      """.trimIndent())
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.testRootDisposable)
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
    val textBlock = PsiTreeUtil.getParentOfType(psiFile.findElementAt(myFixture.editor.caretModel.offset), PsiLanguageInjectionHost::class.java)
    val temporaryPlacesRegistry = TemporaryPlacesRegistry.getInstance(myFixture.project)
    temporaryPlacesRegistry.addHost(textBlock, InjectedLanguage.create(JavaLanguage.INSTANCE.id))
    val elements = myFixture.completeBasic()
    val item = elements.first { element -> element.lookupString.contains("cast", ignoreCase = true) }
      .`as`(LookupElementCustomPreviewHolder::class.java)
    assertNotNull(item)
    val preview = item!!.preview(ActionContext.from(myFixture.editor, myFixture.file))
    assertTrue(preview is IntentionPreviewInfo.CustomDiff)
    assertEquals("""
      class Hello {
        String aaa = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                (() o)
            }
        }""${'"'};
      }
    """.trimIndent(), (preview as IntentionPreviewInfo.CustomDiff).modifiedText())
  }

  @Test
  fun testForiPostfixPreview(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.testRootDisposable)
    // language="JAVA"
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Hello {
        @org.intellij.lang.annotations.Language("JAVA") String string = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                a.fori<caret>
            }
        }""${'"'};
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    val item = elements.first { element -> element.lookupString.contains("fori", ignoreCase = true) }
      .`as`(LookupElementCustomPreviewHolder::class.java)
    assertNotNull(item)
    val preview = item!!.preview(ActionContext.from(myFixture.editor, myFixture.file))
    assertTrue(preview is IntentionPreviewInfo.CustomDiff)
    assertEquals("""
      class Hello {
        @org.intellij.lang.annotations.Language("JAVA") String string = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                for (int i = 0; i < a.size(); i++) {
                    
                }
            }
        }""${'"'};
      }
    """.trimIndent(), (preview as IntentionPreviewInfo.CustomDiff).modifiedText())
  }

  @Test
  fun testCastPostfixPerform(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.testRootDisposable)
    Registry.get("postfix.template.mod.completion.enabled").setValue(true, myFixture.testRootDisposable)
    // language="JAVA"
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Hello {
        @org.intellij.lang.annotations.Language("JAVA") String string = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                o.cast<caret>
            }
        }""${'"'};
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    val item = elements.first { element ->
      element is CompletionItemLookupElement &&
      element.item() is PostfixTemplateModCompletionItemProvider.PostfixModCompletionItem &&
      element.lookupString.contains("cast", ignoreCase = true)
    }
    completion.selectItem(item)
    val state = TemplateManagerImpl.getTemplateState(myFixture.editor)
    if (state != null) {
      WriteCommandAction.runWriteCommandAction(myFixture.project) { state.gotoEnd(false) }
    }
    // the caret must end up right after the expansion, exactly as in the non-injected
    // codeInsight/template/postfix/templates/cast/singleExpression_after.java: a coarse updated range would
    // drag it to the start of the injected region instead, leaving the text itself intact
    // language="JAVA"
    myFixture.checkResult("""
      class Hello {
        @org.intellij.lang.annotations.Language("JAVA") String string = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                (() o)<caret>
            }
        }""${'"'};
      }
      """.trimIndent())
  }

  @Test
  fun testCastVarPostfixPerform(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.testRootDisposable)
    Registry.get("postfix.template.mod.completion.enabled").setValue(true, myFixture.testRootDisposable)
    // language="JAVA"
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Hello {
        @org.intellij.lang.annotations.Language("JAVA") String string = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                o.castvar<caret>
            }
        }""${'"'};
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    val item = elements.first { element ->
      element is CompletionItemLookupElement &&
      element.item() is PostfixTemplateModCompletionItemProvider.PostfixModCompletionItem &&
      element.lookupString.contains("castvar", ignoreCase = true)
    }
    completion.selectItem(item)
    val state = TemplateManagerImpl.getTemplateState(myFixture.editor)
    if (state != null) {
      WriteCommandAction.runWriteCommandAction(myFixture.project) { state.gotoEnd(false) }
    }
    // language="JAVA"
    myFixture.checkResult("""
      class Hello {
        @org.intellij.lang.annotations.Language("JAVA") String string = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                  = () o;
            }
        }""${'"'};
      }
      """.trimIndent())
  }

  @Test
  fun testFindDeclaration(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    // language="JAVA"
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class AB {
        @org.intellij.lang.annotations.Language("Java") String ac = ""${'"'}
        class A { 
          void ab(){
              String a = 1;
              System.out.println(a.<caret>);
          }
        }""${'"'};
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertTrue(elements.any { element -> element.lookupString.contains("Declaration", ignoreCase = true) })
  }

  @Test
  fun testVarPostfixInInjectionDoesNotRecurse(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.testRootDisposable)
    Registry.get("postfix.template.mod.completion.enabled").setValue(true, myFixture.testRootDisposable)
    // language="JAVA"
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Hello {
        @org.intellij.lang.annotations.Language("JAVA") String string = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                a.var<caret>
            }
        }""${'"'};
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    val item = elements.first { element ->
      element is CompletionItemLookupElement &&
      element.item() is PostfixTemplateModCompletionItemProvider.PostfixModCompletionItem &&
      element.lookupString.contains("var", ignoreCase = true)
    }
    completion.selectItem(item)
    val state = TemplateManagerImpl.getTemplateState(myFixture.editor)
    if (state != null) {
      WriteCommandAction.runWriteCommandAction(myFixture.project) { state.gotoEnd(false) }
    }
    val text = myFixture.file.text
    assertFalse( text.contains("a.var"))
    assertTrue(text.contains("= a;"))
  }

  @Test
  fun testPostfixInInjectionDeletesTheWholeKey(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.testRootDisposable)
    Registry.get("postfix.template.mod.completion.enabled").setValue(true, myFixture.testRootDisposable)
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Hello {
        @org.intellij.lang.annotations.Language("JAVA") String string = ""${'"'}
        import java.util.List;
        class A {
            void a(Object o, List<String> a, String[] b) {
                a.sou<caret>
            }
        }""${'"'};
      }
      """.trimIndent())
    val elements = myFixture.completeBasic()
    val item = elements.first { element ->
      element is CompletionItemLookupElement &&
      element.item() is PostfixTemplateModCompletionItemProvider.PostfixModCompletionItem &&
      element.lookupString == "soutv"
    }
    completion.selectItem(item)
    val state = TemplateManagerImpl.getTemplateState(myFixture.editor)
    if (state != null) {
      WriteCommandAction.runWriteCommandAction(myFixture.project) { state.gotoEnd(false) }
    }
    val text = myFixture.file.text
    assertTrue(text.contains("""System.out.println("a = " + a);"""), text)
    // the template key must be gone completely: a leftover dot would glue onto the expansion
    assertFalse(text.contains("a.System"), text)
    assertFalse(text.contains("a.sou"), text)
  }
}