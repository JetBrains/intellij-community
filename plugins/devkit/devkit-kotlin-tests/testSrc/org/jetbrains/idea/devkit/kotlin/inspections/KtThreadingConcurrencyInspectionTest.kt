// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.idea.devkit.inspections.ThreadingConcurrencyInspectionTestBase

class KtThreadingConcurrencyInspectionTest : ThreadingConcurrencyInspectionTestBase() {

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

  fun testWithCheckUnannotatedMethodsAnnotatedMethod() {
    runWithCheckUnannotatedMethodsEnabled {
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
        val dummy = <error descr="Unannotated method calls method annotated with '@RequiresEdt'">Dummy(false)</error>
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

  fun testWithCheckUnannotatedMethodsNotInOverridingMethod() {
    myFixture.addClass("""
      public class Parent {
        public void method() {}
      }
    """.trimIndent())

    runWithCheckUnannotatedMethodsEnabled {
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

  fun testWithCheckUnannotatedMethodCallsAnnotatedMethodFix() {
    runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""    
        fun checkUnannotated() {
            <error descr="Unannotated method calls method annotated with '@RequiresEdt'"><caret>edt</error>()
        }
        
        @RequiresEdt 
        fun edt() {}
      """)

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

  fun testWithCheckUnannotatedMethodCallsMultipleAnnotatedMethodFixes() {
    runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""    
        fun checkUnannotated() {
          <error descr="Unannotated method calls method annotated with multiple threading annotations"><caret>multipleAnnotations</error>()
        }
        
        @RequiresReadLock
        @RequiresBackgroundThread
        fun multipleAnnotations() {}
        """)

      val availableIntentions = myFixture.filterAvailableIntentions("Annotate as")
      UsefulTestCase.assertSameElements(availableIntentions.map { it.text },
                                        "Annotate as @RequiresReadLock",
                                        "Annotate as @RequiresBackgroundThread")
    }
  }

  fun testWithCheckUnannotatedNotAllAnnotationsMethodCallsMultipleAnnotatedMethodFix() {
    runWithCheckUnannotatedMethodsEnabled {
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
      UsefulTestCase.assertSameElements(availableIntentions.map { it.text },
                                        "Annotate as @RequiresReadLock",
                                        "Annotate as @RequiresBackgroundThread"
      )
    }
  }

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

  fun testDoNotCheckEventDispatcherMulticaster() {
    addEventDispatcherClass()

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

  fun testRequiresReadLockInsideRequiresEdt() {
    runWithRequiresReadLockInsideRequiresEdtEnabled {
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

  fun testRequiresWriteLockInsideRequiresEdt() {
    runWithRequiresWriteLockInsideRequiresEdtEnabled {
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