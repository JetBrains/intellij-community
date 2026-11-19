// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ThreadingConcurrencyInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val threading by threadingConcurrencyFixture(projectFixture)
  private val myFixture get() = threading.fixture

  @Test
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

  @Test
  fun testWithCheckUnannotatedMethodsAnnotatedMethod() {
    threading.runWithCheckUnannotatedMethodsEnabled {
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

  @Test
  fun testWithCheckUnannotatedMethodsNotInOverridingMethod() {
    myFixture.addClass("""
      public class Parent {
        public void method() {}
      }
    """.trimIndent())

    threading.runWithCheckUnannotatedMethodsEnabled {
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

  @Test
  fun testWithCheckUnannotatedMethodCallsAnnotatedMethodFix() {
    threading.runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""    
        public void checkUnannotated() {
            <error descr="Unannotated method calls method annotated with '@RequiresEdt'"><caret>edt</error>();
        }
        
        @RequiresEdt 
        public void edt() {}
      """)

      timeoutRunBlocking {
        withContext(Dispatchers.EDT) {
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
    }
  }

  @Test
  fun testWithCheckUnannotatedMethodCallsMultipleAnnotatedMethodFixes() {
    threading.runWithCheckUnannotatedMethodsEnabled {
      doTestHighlighting("""    
        public void checkUnannotated() {
          <error descr="Unannotated method calls method annotated with multiple threading annotations"><caret>multipleAnnotations</error>();
        }
        
        @RequiresReadLock
        @RequiresBackgroundThread
        public void multipleAnnotations() {}
        """)

      val availableIntentions = myFixture.filterAvailableIntentions("Annotate method 'checkUnannotated()'")
      assertThat(availableIntentions.map { it.text }).containsOnly("Annotate method 'checkUnannotated()' as '@RequiresReadLock'", "Annotate method 'checkUnannotated()' as '@RequiresBackgroundThread'")
    }
  }

  @Test
  fun testWithCheckUnannotatedNotAllAnnotationsMethodCallsMultipleAnnotatedMethodFix() {
    threading.runWithCheckUnannotatedMethodsEnabled {
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
      assertThat(availableIntentions.map { it.text }).containsOnly("Annotate method 'checkNotAllAnnotationsAndViolation()' as '@RequiresBackgroundThread'")
    }
  }

  @Test
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

  @Test
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

  @Test
  fun testDoNotCheckEventDispatcherMulticaster() {
    threading.addEventDispatcherClass()

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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  fun testRequiresReadLockInsideRequiresEdt() {
    threading.runWithRequiresReadLockInsideRequiresEdtEnabled {
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

  @Test
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

  @Test
  fun testRequiresWriteLockInsideRequiresEdt() {
    threading.runWithRequiresWriteLockInsideRequiresEdtEnabled {
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

  @Test
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