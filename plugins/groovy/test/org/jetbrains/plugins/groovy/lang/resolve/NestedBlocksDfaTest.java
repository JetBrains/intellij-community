// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.RecursionManager;
import org.jetbrains.plugins.groovy.util.Groovy30Test;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.jetbrains.plugins.groovy.util.TypingTest;
import org.junit.Before;
import org.junit.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class NestedBlocksDfaTest extends Groovy30Test implements TypingTest, HighlightingTest {
  @Before
  public void disableRecursion() {
    RecursionManager.assertOnRecursionPrevention(getFixture().getTestRootDisposable());
  }

  @Test
  public void outer_reference() {
    typingTest("""                 
                 def a = 1;
                 {
                   <caret>a
                 }
                 """, JAVA_LANG_INTEGER);
  }

  @Test
  public void reassign_in_nested_block() {
    typingTest("""                 
                 def a = 1;
                 {
                   a = 'str'
                 }
                 <caret>a
                 """, JAVA_LANG_STRING);
  }

  @Test
  public void reassign_in_double_nested_block() {
    typingTest("""                 
                 def a = 1;
                 {
                   {
                     a = 'str'
                   }
                 }
                 <caret>a
                 """, JAVA_LANG_STRING);
  }

  @Test
  public void defined_in_nested_block() {
    typingTest("""                 
                 {
                   def a = 'str'
                 }
                 <caret>a
                 """, null);
  }

  @Test
  public void defined_in_while_block() {
    typingTest("""                 
                 while ("".equals("sad")) {
                   def a = 'str'
                 }
                 <caret>a
                 """, null);
  }

  @Test
  public void defined_in_try_block() {
    typingTest("""                 
                 try {
                     def a = 1
                 } finally {
                     <caret>a
                 }
                 """, null);
  }

  @Test
  public void twice_defined_in_nested_blocks() {
    typingTest("""                 
                 {
                     def a = 1
                 }
                 
                 {
                     def a = ""
                 }
                 <caret>a
                 """, null);
  }

  @Test
  public void binding_defined_in_nested_blocks() {
    typingTest("""                 
                 {
                     a = 1
                 }
                 
                 {
                     a = ""
                 }
                 <caret>a
                 """, JAVA_LANG_STRING);
  }

  @Test
  public void binding_and_local_var_defined_in_nested_blocks() {
    typingTest("""                 
                 {
                     a = 1
                 }
                 
                 {
                    def a = ""
                 }
                 <caret>a
                 """, JAVA_LANG_INTEGER);
  }

  @Test
  public void filed_and_local_var_defined_in_nested_block() {
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
  }

  @Test
  public void filed_and_local_var_defined_in_nested_block_2() {
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
  }

  @Test
  public void highlight_local_field_initialized_twice() {
    highlightingTest("""                       
                       
                           def a = "String";
                           {
                               def <error descr="Variable 'a' already defined">a</error> = 1
                           }
                       """);
  }

  @Test
  public void highlight_local_field_in_nested_blocks() {
    highlightingTest("""                       
                       {
                           def a = "String";
                       }
                       
                       {
                           def a = 1
                       }
                       """);
  }
}
