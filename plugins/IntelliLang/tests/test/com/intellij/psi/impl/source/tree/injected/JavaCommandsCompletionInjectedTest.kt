// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.completion.command.CommandCompletionLookupElement
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry

@NeedsIndex.SmartMode(reason = "it requires highlighting")
class JavaCommandsCompletionInjectedTest : LightFixtureCompletionTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_21
  }

  fun testFlip() {
    // language="JAVA"
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
    selectItem(elements.first { element -> element.lookupString.contains("Flip", ignoreCase = true) })
    // language="JAVA"
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

  fun testFlipPreview() {
    // language="JAVA"
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
    val preview = lookupElement!!.preview as? IntentionPreviewInfo.CustomDiff
    // language="JAVA"
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


  fun testFlipPreviewTemporaryEnabled() {
    // language="JAVA"
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
    val textBlock = PsiTreeUtil.getParentOfType(psiFile.findElementAt(editor.caretModel.offset), PsiLanguageInjectionHost::class.java)
    val temporaryPlacesRegistry = TemporaryPlacesRegistry.getInstance(project)
    temporaryPlacesRegistry.addHost(textBlock, InjectedLanguage.create(JavaLanguage.INSTANCE.id))
    val elements = myFixture.completeBasic()
    val lookupElement = elements
      .firstOrNull { element -> element.lookupString.contains("Flip", ignoreCase = true) }
      ?.`as`(CommandCompletionLookupElement::class.java)
    assertNotNull(lookupElement)
    val preview = lookupElement!!.preview as? IntentionPreviewInfo.CustomDiff
    // language="JAVA"
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

  fun testFindDeclaration() {
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
}