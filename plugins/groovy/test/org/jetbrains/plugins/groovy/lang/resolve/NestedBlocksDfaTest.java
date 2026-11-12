// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.TypingTest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class NestedBlocksDfaTest implements TypingTest, HighlightingTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_3_0);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @BeforeEach
  public void disableRecursion() {
    runInEdtAndWait(() -> RecursionManager.assertOnRecursionPrevention(getFixture().getTestRootDisposable()));
  }

  @Test
  public void outer_reference() {
    runInEdtAndWait(() -> {
      typingTest("""                 
                   def a = 1;
                   {
                     <caret>a
                   }
                   """, JAVA_LANG_INTEGER);
    });
  }

  @Test
  public void reassign_in_nested_block() {
    runInEdtAndWait(() -> {
      typingTest("""                 
                   def a = 1;
                   {
                     a = 'str'
                   }
                   <caret>a
                   """, JAVA_LANG_STRING);
    });
  }

  @Test
  public void reassign_in_double_nested_block() {
    runInEdtAndWait(() -> {
      typingTest("""                 
                   def a = 1;
                   {
                     {
                       a = 'str'
                     }
                   }
                   <caret>a
                   """, JAVA_LANG_STRING);
    });
  }

  @Test
  public void defined_in_nested_block() {
    runInEdtAndWait(() -> {
      typingTest("""                 
                   {
                     def a = 'str'
                   }
                   <caret>a
                   """, null);
    });
  }

  @Test
  public void defined_in_while_block() {
    runInEdtAndWait(() -> {
      typingTest("""                 
                   while ("".equals("sad")) {
                     def a = 'str'
                   }
                   <caret>a
                   """, null);
    });
  }

  @Test
  public void defined_in_try_block() {
    runInEdtAndWait(() -> {
      typingTest("""                 
                   try {
                       def a = 1
                   } finally {
                       <caret>a
                   }
                   """, null);
    });
  }

  @Test
  public void twice_defined_in_nested_blocks() {
    runInEdtAndWait(() -> {
      typingTest("""                 
                   {
                       def a = 1
                   }
                 
                   {
                       def a = ""
                   }
                   <caret>a
                   """, null);
    });
  }

  @Test
  public void binding_defined_in_nested_blocks() {
    runInEdtAndWait(() -> {
      typingTest("""                 
                   {
                       a = 1
                   }
                 
                   {
                       a = ""
                   }
                   <caret>a
                   """, JAVA_LANG_STRING);
    });
  }

  @Test
  public void binding_and_local_var_defined_in_nested_blocks() {
    runInEdtAndWait(() -> {
      typingTest("""                 
                   {
                       a = 1
                   }
                 
                   {
                      def a = ""
                   }
                   <caret>a
                   """, JAVA_LANG_INTEGER);
    });
  }

  @Test
  public void filed_and_local_var_defined_in_nested_block() {
    runInEdtAndWait(() -> {
      typingTest("""                 
                   class A {
                       def a = "String"
                       def m() {
                           {
                               def a = 1
                           }
                           <caret>a
                 
                       }
                   }
                   """, JAVA_LANG_STRING);
    });
  }

  @Test
  public void filed_and_local_var_defined_in_nested_block_2() {
    runInEdtAndWait(() -> {
      typingTest("""                 
                   class A {
                       def a = "String"
                       def m() {
                           {
                               def a = 1
                               <caret>a
                           }
                       }
                   }
                   """, JAVA_LANG_INTEGER);
    });
  }

  @Test
  public void highlight_local_field_initialized_twice() {
    runInEdtAndWait(() -> {
      highlightingTest("""                       
                       
                             def a = "String";
                             {
                                 def <error descr="Variable 'a' already defined">a</error> = 1
                             }
                         """);
    });
  }

  @Test
  public void highlight_local_field_in_nested_blocks() {
    runInEdtAndWait(() -> {
      highlightingTest("""                       
                         {
                             def a = "String";
                         }
                       
                         {
                             def a = 1
                         }
                         """);
    });
  }
}
