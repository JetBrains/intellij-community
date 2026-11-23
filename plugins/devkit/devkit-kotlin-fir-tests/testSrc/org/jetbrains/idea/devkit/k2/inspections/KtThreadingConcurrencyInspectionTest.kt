// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.idea.devkit.inspections.threadingConcurrencyFixture
import org.junit.jupiter.api.Test

@TestApplication
class KtThreadingConcurrencyInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val threading by threadingConcurrencyFixture(projectFixture)
  private val myFixture get() = threading.fixture

  @Test
  fun testWithCheckUnannotatedMethodsDisabledAnnotatedMethod() {
    doTestHighlighting("""
      fun doNotCare() {
        edt()
        bgt()
        readLock()
        writeLock()
        absence()
      }
      
       @RequiresEdt 
       fun edt() {}
       
       @RequiresBackgroundThread
       fun bgt() {}
       
       @RequiresReadLock
       fun readLock() {}
       
       @RequiresWriteLock
       fun writeLock() {}
       
       @RequiresReadLockAbsence
       fun absence() {}
    """)
  }

  @Test
  fun testWithCheckUnannotatedMethodsAnnotatedMethod() {
    threading.runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""
      private fun checkUnannotatedNotInPrivateMethod() {
        edt()
      }     
       
      protected fun checkUnannotatedNotInProtectedMethod() {
        edt()
      }
        
      fun checkUnannotated() {
        <error descr="Unannotated method calls method annotated with '@RequiresEdt'">edt</error>()
        <error descr="Unannotated method calls method annotated with '@RequiresBackgroundThread'">bgt</error>()
        <error descr="Unannotated method calls method annotated with '@RequiresReadLock'">readLock</error>()
        <error descr="Unannotated method calls method annotated with '@RequiresWriteLock'">writeLock</error>()
        <error descr="Unannotated method calls method annotated with '@RequiresReadLockAbsence'">absence</error>()
        
        
        // nested calls --------------------------------------
        if (<error descr="Unannotated method calls method annotated with '@RequiresEdt'">edt</error>()) {
          return
        }
        
        while (<error descr="Unannotated method calls method annotated with '@RequiresEdt'">edt</error>()) {}
        
        
        // CTOR --------------------
        val dummy = <error descr="Unannotated method calls method annotated with '@RequiresEdt'">Dummy</error>(false)
      }
      
      private class Dummy @RequiresEdt constructor(val dummy : Boolean)
      
      @RequiresEdt 
      fun edt(): Boolean { return false }
      
      @RequiresBackgroundThread
      fun bgt() {}
      
      @RequiresReadLock
      fun readLock() {}
      
      @RequiresWriteLock
      fun writeLock() {}
      
      @RequiresReadLockAbsence
      fun absence() {}
    """)
    }
  }

  @Test
  fun testWithCheckUnannotatedMethodsNotInOverridingMethod() {
    myFixture.addClass("""
      public class Parent {
        public void method() {}
      }
    """.trimIndent())

    threading.runWithCheckUnannotatedMethodsEnabled {
      myFixture.configureByText("Subclass.kt", """
        import com.intellij.util.concurrency.annotations.*

        class Subclass : Parent() {
          override fun method() {
            edt()
          }
          
          @RequiresEdt 
          fun edt() {}
        }
      """.trimIndent())

      myFixture.checkHighlighting()
    }
  }

  @Test
  fun testWithCheckUnannotatedMethodCallsAnnotatedMethodFix() {
    threading.runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""    
        fun checkUnannotated() {
            <error descr="Unannotated method calls method annotated with '@RequiresEdt'"><caret>edt</error>()
        }
        
        @RequiresEdt 
        fun edt() {}
      """)

      timeoutRunBlocking {
        withContext(Dispatchers.EDT) {
          val intention = myFixture.findSingleIntention("Annotate as @RequiresEdt")
          myFixture.checkPreviewAndLaunchAction(intention)
          myFixture.checkResult("""
            import com.intellij.util.concurrency.annotations.*

            class A {

                @RequiresEdt
                fun checkUnannotated() {
                    edt()
                }

                @RequiresEdt
                fun edt() {}

            }
          """.trimIndent(), true)
        }
      }
    }
  }

  @Test
  fun testWithCheckUnannotatedMethodCallsMultipleAnnotatedMethodFixes() {
    threading.runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""    
        fun checkUnannotated() {
          <error descr="Unannotated method calls method annotated with multiple threading annotations"><caret>multipleAnnotations</error>()
        }
        
        @RequiresReadLock
        @RequiresBackgroundThread
        fun multipleAnnotations() {}
        """)

      val availableIntentions = myFixture.filterAvailableIntentions("Annotate as")
      assertThat(availableIntentions.map { it.text }).containsOnly("Annotate as @RequiresReadLock", "Annotate as @RequiresBackgroundThread")
    }
  }

  @Test
  fun testWithCheckUnannotatedNotAllAnnotationsMethodCallsMultipleAnnotatedMethodFix() {
    threading.runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""    
        @RequiresReadLock
        fun checkNotAllAnnotationsAndViolation() {
          // missing
          <error descr="Unannotated method calls method annotated with '@RequiresBackgroundThread'"><caret>multipleAnnotations</error>()
          
          // violation
          <error descr="Method annotated with '@RequiresWriteLock' must not be called from method annotated with '@RequiresReadLock'">writeLock</error>()
        }
        
        @RequiresReadLock
        @RequiresBackgroundThread
        fun multipleAnnotations() {}
        
        @RequiresWriteLock
        fun writeLock() {}
        """)

      val availableIntentions = myFixture.filterAvailableIntentions("Annotate as")
      assertThat(availableIntentions.map { it.text }).containsOnly("Annotate as @RequiresReadLock", "Annotate as @RequiresBackgroundThread")
    }
  }

  @Test
  fun testDoNotCheckLambdaReturn() {
    doTestHighlighting("""
      @RequiresBackgroundThread
      fun lambdaReturn(): Runnable {
        return Runnable { edt() }
      }
      
      @RequiresEdt
      fun edt() {}
    """.trimIndent())
  }

  @Test
  fun testDoNotCheckAnonymous() {
    doTestHighlighting("""
      @RequiresEdt
      fun edt() {
        object: MyRunnable() {
          override fun run() {
            bgt()
          }
        }
      }

      abstract class MyRunnable: Runnable {}
      
      @RequiresBackgroundThread
      fun bgt() {}
    """.trimIndent())
  }

  @Test
  fun testDoNotCheckEventDispatcherMulticaster() {
    threading.addEventDispatcherClass()

    doTestHighlighting("""
      @RequiresBackgroundThread
      fun testEventDispatcher() {
        val dispatcher: com.intellij.util.EventDispatcher<MyListener> = com.intellij.util.EventDispatcher()
        dispatcher.getMulticaster().myCallback()
        dispatcher.multicaster.myCallback()
        
        val listener: MyListener = object : MyListener {
          @RequiresEdt
          override fun myCallback() {}
        }
        listener.<error descr="Method annotated with '@RequiresEdt' must not be called from method annotated with '@RequiresBackgroundThread'">myCallback</error>()
      }
      
      interface MyListener : java.util.EventListener {
        
        @RequiresEdt
        fun myCallback()
      }
    """.trimIndent())
  }

  @Test
  fun testMayCallRequiresEdtCalledFromRequiresEdtMethod() {
    doTestHighlighting("""
      @RequiresEdt
      fun edt() {
        edt2()
      }
      
      @RequiresEdt
      fun edt2() {}
    """)
  }

  @Test
  fun testRequiresBackgroundThreadVersusRequiresEdtMethod() {
    doTestHighlighting("""
      @RequiresEdt
      fun edt() {
        <error descr="Method annotated with '@RequiresBackgroundThread' must not be called from method annotated with '@RequiresEdt'">bgt</error>()
      }
    
      @RequiresBackgroundThread
      fun bgt() {
        <error descr="Method annotated with '@RequiresEdt' must not be called from method annotated with '@RequiresBackgroundThread'">edt</error>()
      }
     """)
  }

  @Test
  fun testRequiresWriteLockInsideRequiresReadLock() {
    doTestHighlighting("""
      @RequiresReadLock
      fun readLock() {
          <error descr="Method annotated with '@RequiresWriteLock' must not be called from method annotated with '@RequiresReadLock'">writeLock</error>()
      }
      
      @RequiresWriteLock
      fun writeLock() {
        readLock()
      }
    """)
  }

  @Test
  fun testRequiresReadLockAbsenceVersusRequiresReadOrWriteLock() {
    doTestHighlighting("""
      @RequiresReadLock
      fun readLock() {
        <error descr="Method annotated with '@RequiresReadLockAbsence' must not be called from method annotated with '@RequiresReadLock'">absence</error>()
      }
      
      @RequiresWriteLock
      fun writeLock() {
        <error descr="Method annotated with '@RequiresReadLockAbsence' must not be called from method annotated with '@RequiresWriteLock'">absence</error>()
      }
      
      @RequiresReadLockAbsence
      fun absence() {
        <error descr="Method annotated with '@RequiresReadLock' must not be called from method annotated with '@RequiresReadLockAbsence'">readLock</error>()
        
        <error descr="Method annotated with '@RequiresWriteLock' must not be called from method annotated with '@RequiresReadLockAbsence'">writeLock</error>()
      }
    """)
  }

  @Test
  fun testRequiresReadLockInsideRequiresEdt() {
    threading.runWithRequiresReadLockInsideRequiresEdtEnabled {
      doTestHighlighting("""
      @RequiresEdt
      fun edt() {
        <error descr="Method annotated with '@RequiresReadLock' must not be called from method annotated with '@RequiresEdt'">readLock</error>()
      }
      
      @RequiresReadLock
      fun readLock() {}
    """)
    }
  }

  @Test
  fun testRequiresReadLockInsideRequiresEdtOptionDisabled() {
    doTestHighlighting("""
      @RequiresEdt
      fun edt() {
        readLock()
      }
      
      @RequiresReadLock
      fun readLock() {}
    """)
  }

  @Test
  fun testRequiresWriteLockInsideRequiresEdt() {
    threading.runWithRequiresWriteLockInsideRequiresEdtEnabled {
      doTestHighlighting("""
      @RequiresEdt
      fun edt() {
        <error descr="Method annotated with '@RequiresWriteLock' must not be called from method annotated with '@RequiresEdt'">writeLock</error>()
      }
      
      @RequiresWriteLock
      fun writeLock() {}
    """)
    }
  }

  @Test
  fun testRequiresWriteLockInsideRequiresEdtOptionDisabled() {
    doTestHighlighting("""
      @RequiresEdt
      fun edt() {
        writeLock()
      }
      
      @RequiresWriteLock
      fun writeLock() {}
    """)
  }

  private fun doTestHighlighting(classBody: String) {
    myFixture.configureByText("A.kt", """
    import com.intellij.util.concurrency.annotations.*

    class A {
      ${classBody}
    }
    """.trimIndent())

    myFixture.checkHighlighting(false, false, false)
  }
}