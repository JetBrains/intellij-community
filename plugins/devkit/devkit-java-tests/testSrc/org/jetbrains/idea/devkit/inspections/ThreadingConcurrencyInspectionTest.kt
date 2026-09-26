// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.UsefulTestCase

class ThreadingConcurrencyInspectionTest : ThreadingConcurrencyInspectionTestBase() {

  fun testWithCheckUnannotatedMethodsDisabledAnnotatedMethod() {
    doTestHighlighting("""
      public void doNotCare() {
        edt();
        bgt();
        readLock();
        writeLock();
        absence();
      }
      
       @RequiresEdt 
       public void edt() {}
       
       @RequiresBackgroundThread
       private void bgt() {}
       
       @RequiresReadLock
       public void readLock() {}
       
       @RequiresWriteLock
       public void writeLock() {}
       
       @RequiresReadLockAbsence
       public void absence() {}
    """)
  }

  fun testWithCheckUnannotatedMethodsAnnotatedMethod() {
    runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""
      private void checkUnannotatedNotInPrivateMethod() {
        edt();
      }     
       
      protected void checkUnannotatedNotInProtectedMethod() {
        edt();
      }
        
      public void checkUnannotated() {
        <error descr="Unannotated method calls method annotated with '@RequiresEdt'">edt</error>();
        <error descr="Unannotated method calls method annotated with '@RequiresBackgroundThread'">bgt</error>();
        <error descr="Unannotated method calls method annotated with '@RequiresReadLock'">readLock</error>();
        <error descr="Unannotated method calls method annotated with '@RequiresWriteLock'">writeLock</error>();
        <error descr="Unannotated method calls method annotated with '@RequiresReadLockAbsence'">absence</error>();
        
        
        // nested calls --------------------------------------
        if (<error descr="Unannotated method calls method annotated with '@RequiresEdt'">edt</error>()) {
          return;
        }
        
        while (<error descr="Unannotated method calls method annotated with '@RequiresEdt'">edt</error>()) {}
        
        
        // CTOR --------------------
        Dummy dummy = new <error descr="Unannotated method calls method annotated with '@RequiresEdt'">Dummy</error>(false);
      }
      
      private class Dummy { 
        @RequiresEdt
        private Dummy(boolean dummy) {}
      }
      
      @RequiresEdt 
      public boolean edt() { return false; }
      
      @RequiresBackgroundThread
      private void bgt() {}
      
      @RequiresReadLock
      public void readLock() {}
      
      @RequiresWriteLock
      public void writeLock() {}
      
      @RequiresReadLockAbsence
      public void absence() {}
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
      myFixture.configureByText("Subclass.java", """
        import com.intellij.util.concurrency.annotations.*;

        public class Subclass extends Parent {
          @Override
          public void method() {
            edt();
          }
          
          @RequiresEdt 
          public void edt() {}
        }
      """.trimIndent())

      myFixture.checkHighlighting()
    }
  }

  fun testWithCheckUnannotatedMethodCallsAnnotatedMethodFix() {
    runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""    
        public void checkUnannotated() {
            <error descr="Unannotated method calls method annotated with '@RequiresEdt'"><caret>edt</error>();
        }
        
        @RequiresEdt 
        public void edt() {}
      """)

      val intention = myFixture.findSingleIntention("Annotate method 'checkUnannotated()' as '@RequiresEdt'")
      myFixture.checkPreviewAndLaunchAction(intention)
      myFixture.checkResult("""
        import com.intellij.util.concurrency.annotations.*;

        public class A {

            @RequiresEdt
            public void checkUnannotated() {
                edt();
            }

            @RequiresEdt
            public void edt() {}

        }
      """.trimIndent(), true)
    }
  }

  fun testWithCheckUnannotatedMethodCallsMultipleAnnotatedMethodFixes() {
    runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""    
        public void checkUnannotated() {
          <error descr="Unannotated method calls method annotated with multiple threading annotations"><caret>multipleAnnotations</error>();
        }
        
        @RequiresReadLock
        @RequiresBackgroundThread
        public void multipleAnnotations() {}
        """)

      val availableIntentions = myFixture.filterAvailableIntentions("Annotate method 'checkUnannotated()'")
      UsefulTestCase.assertSameElements(availableIntentions.map { it.text },
                                        "Annotate method 'checkUnannotated()' as '@RequiresReadLock'",
                                        "Annotate method 'checkUnannotated()' as '@RequiresBackgroundThread'"
      )
    }
  }

  fun testWithCheckUnannotatedNotAllAnnotationsMethodCallsMultipleAnnotatedMethodFix() {
    runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""    
        @RequiresReadLock
        public void checkNotAllAnnotationsAndViolation() {
          // missing
          <error descr="Unannotated method calls method annotated with '@RequiresBackgroundThread'"><caret>multipleAnnotations</error>();
          
          // violation
          <error descr="Method annotated with '@RequiresWriteLock' must not be called from method annotated with '@RequiresReadLock'">writeLock</error>();
        }
        
        @RequiresReadLock
        @RequiresBackgroundThread
        public void multipleAnnotations() {}
        
        @RequiresWriteLock
        public void writeLock() {}
        """)

      val availableIntentions = myFixture.filterAvailableIntentions("Annotate method 'checkNotAllAnnotationsAndViolation()'")
      UsefulTestCase.assertSameElements(availableIntentions.map { it.text },
                                        "Annotate method 'checkNotAllAnnotationsAndViolation()' as '@RequiresBackgroundThread'"
      )
    }
  }

  fun testDoNotCheckLambdaReturn() {
    doTestHighlighting("""
      @RequiresBackgroundThread
      public Runnable lambdaReturn() {
        return () -> {
          edt();
        };
      }
      
      @RequiresEdt
      private void edt() {}
    """.trimIndent())
  }

  fun testDoNotCheckAnonymous() {
    doTestHighlighting("""
      @RequiresEdt
      public void edt() {
        MyRunnable my = new MyRunnable() {
          public void run() {
            bgt();
          }
        };
      }

      public abstract class MyRunnable implements Runnable {}
      
      @RequiresBackgroundThread
      public void bgt() {}
    """.trimIndent())
  }

  fun testDoNotCheckEventDispatcherMulticaster() {
    addEventDispatcherClass()

    doTestHighlighting("""
      @RequiresBackgroundThread
      public void testEventDispatcher() {
        com.intellij.util.EventDispatcher<MyListener> dispatcher = new com.intellij.util.EventDispatcher<>();
        dispatcher.getMulticaster().myCallback();
        
        MyListener listener = new MyListener() {
          public void myCallback() {};
        };
        listener.<error descr="Method annotated with '@RequiresEdt' must not be called from method annotated with '@RequiresBackgroundThread'">myCallback</error>();
      }
      
      public interface MyListener extends java.util.EventListener {
        
        @RequiresEdt
        void myCallback();
      }
    """.trimIndent())
  }

  fun testMayCallRequiresEdtCalledFromRequiresEdtMethod() {
    doTestHighlighting("""
      @RequiresEdt
      public void edt() {
        edt2();
      }
      
      @RequiresEdt
      private void edt2() {}
    """)
  }

  fun testRequiresBackgroundThreadVersusRequiresEdtMethod() {
    doTestHighlighting("""
      @RequiresEdt
      public void edt() {
        <error descr="Method annotated with '@RequiresBackgroundThread' must not be called from method annotated with '@RequiresEdt'">bgt</error>();
      }

      @RequiresBackgroundThread
      private void bgt() {
        <error descr="Method annotated with '@RequiresEdt' must not be called from method annotated with '@RequiresBackgroundThread'">edt</error>();
      }
     """)
  }

  fun testRequiresWriteLockInsideRequiresReadLock() {
    doTestHighlighting("""
      @RequiresReadLock
      public void readLock() {
          <error descr="Method annotated with '@RequiresWriteLock' must not be called from method annotated with '@RequiresReadLock'">writeLock</error>();
      }
      
      @RequiresWriteLock
      private void writeLock() {
        readLock();
      }
    """)
  }

  fun testRequiresReadLockAbsenceVersusRequiresReadOrWriteLock() {
    doTestHighlighting("""
      @RequiresReadLock
      public void readLock() {
          <error descr="Method annotated with '@RequiresReadLockAbsence' must not be called from method annotated with '@RequiresReadLock'">absence</error>();
      }
      
      @RequiresWriteLock
      public void writeLock() {
        <error descr="Method annotated with '@RequiresReadLockAbsence' must not be called from method annotated with '@RequiresWriteLock'">absence</error>();
      }
      
      @RequiresReadLockAbsence
      public void absence() {
        <error descr="Method annotated with '@RequiresReadLock' must not be called from method annotated with '@RequiresReadLockAbsence'">readLock</error>();
        
        <error descr="Method annotated with '@RequiresWriteLock' must not be called from method annotated with '@RequiresReadLockAbsence'">writeLock</error>();
      }
    """)
  }

  fun testRequiresReadLockInsideRequiresEdt() {
    runWithRequiresReadLockInsideRequiresEdtEnabled {
      doTestHighlighting("""
      @RequiresEdt
      public void edt() {
          <error descr="Method annotated with '@RequiresReadLock' must not be called from method annotated with '@RequiresEdt'">readLock</error>();
      }
      
      @RequiresReadLock
      private void readLock() {}
    """)
    }
  }

  fun testRequiresReadLockInsideRequiresEdtOptionDisabled() {
    doTestHighlighting("""
      @RequiresEdt
      public void edt() {
        readLock();
      }
      
      @RequiresReadLock
      private void readLock() {}
    """)
  }

  fun testRequiresWriteLockInsideRequiresEdt() {
    runWithRequiresWriteLockInsideRequiresEdtEnabled {
      doTestHighlighting("""
      @RequiresEdt
      public void edt() {
          <error descr="Method annotated with '@RequiresWriteLock' must not be called from method annotated with '@RequiresEdt'">writeLock</error>();
      }
      
      @RequiresWriteLock
      private void writeLock() {}
    """)
    }
  }

  fun testRequiresWriteLockInsideRequiresEdtOptionDisabled() {
    doTestHighlighting("""
      @RequiresEdt
      public void edt() {
        writeLock();
      }
      
      @RequiresWriteLock
      private void writeLock() {}
    """)
  }

  private fun doTestHighlighting(classBody: String) {
    myFixture.configureByText("A.java", """
    import com.intellij.util.concurrency.annotations.*;
      
    public class A {
      ${classBody}
    }
    """.trimIndent())

    myFixture.checkHighlighting(false, false, false)
  }

}