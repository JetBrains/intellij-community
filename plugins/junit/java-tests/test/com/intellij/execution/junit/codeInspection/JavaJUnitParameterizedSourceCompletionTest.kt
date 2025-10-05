// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitParameterizedSourceCompletionTestBase

class JavaJUnitParameterizedSourceCompletionTest : JUnitParameterizedSourceCompletionTestBase() {
  fun `test completion method source`() {
    myFixture.configureByText("Test.java", """ 
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      import java.util.List;
      import java.util.ArrayList;
      import java.util.stream.Stream;

      class Test {
        @ParameterizedTest
        @MethodSource("<caret>")
        public void foo(Integer i) { }

        public static Stream<Integer> abc() {
            return Stream.of(1, 2, 3);
        }
      }
    """)
    myFixture.testCompletionVariants(file.name, "abc")
  }

  fun `test completion method source several methods`() {
    myFixture.configureByText("Test.java", """ 
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      import java.util.List;
      import java.util.ArrayList;
      import java.util.stream.Stream;

      class Test {
        @ParameterizedTest
        @MethodSource("<caret>")
        public void foo(Integer i) { }

        public static Stream<Integer> aaa() {
            return Stream.of(1, 2, 3);
        }
        
        public static Stream<Integer> bbb() {
            return Stream.of(1, 2, 3);
        }
        
        public static Stream<Integer> ccc() {
            return Stream.of(1, 2, 3);
        }
        
        public static Stream<Integer> ddd() {
            return Stream.of(1, 2, 3);
        }
      }
    """)
    myFixture.testCompletionVariants(file.name, "aaa", "bbb", "ccc", "ddd")
  }

  fun `test completion method source non-static method`() {
    myFixture.configureByText("Test.java", """ 
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      import java.util.List;
      import java.util.ArrayList;
      import java.util.stream.Stream;

      class Test {
        @ParameterizedTest
        @MethodSource("<caret>")
        public void foo(Integer i) { }

        public static Stream<Integer> aaa() {
            return Stream.of(1, 2, 3);
        }
        
        public static Stream<Integer> bbb() {
            return Stream.of(1, 2, 3);
        }
        
        public static Stream<Integer> ccc() {
            return Stream.of(1, 2, 3);
        }
        
        public Stream<Integer> ddd() {
            return Stream.of(1, 2, 3);
        }
      }
    """)
    myFixture.testCompletionVariants(file.name, "aaa", "bbb", "ccc")
  }
  fun `test completion method source non-static method with annotation`() {
    myFixture.configureByText("Test.java", """ 
      import org.junit.jupiter.api.TestInstance;
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      import java.util.List;
      import java.util.ArrayList;
      import java.util.stream.Stream;
      
      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      class Test {
        @ParameterizedTest
        @MethodSource("<caret>")
        public void foo(Integer i) { }

        public static Stream<Integer> aaa() {
            return Stream.of(1, 2, 3);
        }
        
        public static Stream<Integer> bbb() {
            return Stream.of(1, 2, 3);
        }
        
        public static Stream<Integer> ccc() {
            return Stream.of(1, 2, 3);
        }
        
        public Stream<Integer> ddd() {
            return Stream.of(1, 2, 3);
        }
      }
    """)
    myFixture.testCompletionVariants(file.name, "ddd")
  }
  fun `test completion field source`() {
    myFixture.configureByText("Test.java", """ 
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.FieldSource;
      import java.util.List;
      import java.util.ArrayList;

      class Test {
        @ParameterizedTest
        @FieldSource("<caret>")
        public void foo(Integer i) { }

        public static List<Integer> abc = new ArrayList<>();
      }
      """)
    myFixture.testCompletionVariants(file.name, "abc")
  }
  fun `test completion field source several fields`() {
    myFixture.configureByText("Test.java", """ 
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.FieldSource;
      import java.util.List;
      import java.util.ArrayList;

      class Test {
        @ParameterizedTest
        @FieldSource("<caret>")
        public void foo(Integer i) { }

        public static List<Integer> aaa = new ArrayList<>();
        public static List<Integer> bbb = new ArrayList<>();
        public static List<Integer> ccc = new ArrayList<>();
        public static List<Integer> ddd = new ArrayList<>();
      }
      """)
    myFixture.testCompletionVariants(file.name, "aaa", "bbb", "ccc", "ddd")
  }

  fun `test completion field source non-static field`() {
    myFixture.configureByText("Test.java", """ 
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.FieldSource;
      import java.util.List;
      import java.util.ArrayList;

      class Test {
        @ParameterizedTest
        @FieldSource("<caret>")
        public void foo(Integer i) { }

        public static List<Integer> aaa = new ArrayList<>();
        public static List<Integer> bbb = new ArrayList<>();
        public static List<Integer> ccc = new ArrayList<>();
        public List<Integer> ddd = new ArrayList<>();
      }
      """)
    myFixture.testCompletionVariants(file.name, "aaa", "bbb", "ccc")
  }

  fun `test completion field source non-static field with annotation`() {
    myFixture.configureByText("Test.java", """ 
      import org.junit.jupiter.api.TestInstance;
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.FieldSource;
      import java.util.List;
      import java.util.ArrayList;

      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      class Test {
        @ParameterizedTest
        @FieldSource("<caret>")
        public void foo(Integer i) { }

        public static List<Integer> aaa = new ArrayList<>();
        public static List<Integer> bbb = new ArrayList<>();
        public static List<Integer> ccc = new ArrayList<>();
        public List<Integer> ddd = new ArrayList<>();
      }
      """)
    myFixture.testCompletionVariants(file.name, "aaa", "bbb", "ccc", "ddd")
  }
}