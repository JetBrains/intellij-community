/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.xmlb;

import com.intellij.util.Function;
import org.junit.Test;

import static com.intellij.util.xmlb.XmlSerializerUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class XmlSerializerUtilTest {
  private static <T extends A> void doTestCopyBean(T from, B to, Function<Void, Void> test) {
    String expectedFoo = from.foo;
    String expectedBar = to.bar;

    test.fun(null);

    assertEquals(expectedFoo, from.foo);
    assertEquals(expectedFoo, to.foo);
    assertEquals(expectedBar, to.bar);
  }

  @Test
  public void testSimpleCopyBean() throws Exception {
    final A from = new A();
    final B to = new B();
    doTestCopyBean(from, to, new Function<Void, Void>() {
      @Override
      public Void fun(Void aVoid) {
        copyBean(from, to);
        return null;
      }
    });
  }

  @Test
  public void testCustomCopyBean() throws Exception {
    final C from = new C();
    final B to = new B();
    doTestCopyBean(from, to, new Function<Void, Void>() {
      @Override
      public Void fun(Void aVoid) {
        copyBean(from, to, A.class);
        return null;
      }
    });
  }

  private static <T extends A> void doTestMergeBeans(T from, B to, Function<Void, B> test) {
    String expectedFoo = from.foo;
    String expectedBFoo = to.foo;
    String expectedBar = to.bar;

    B copy = test.fun(null);

    assertNotNull(copy);
    assertEquals(expectedFoo, from.foo);
    assertEquals(expectedBFoo, to.foo);
    assertEquals(expectedBar, to.bar);
    assertEquals(expectedFoo, copy.foo);
    assertEquals(expectedBar, copy.bar);
  }

  @Test
  public void testSimpleMergeBeans() throws Exception {
    final A from = new A();
    final B to = new B();
    doTestMergeBeans(from, to, new Function<Void, B>() {
      @Override
      public B fun(Void aVoid) {
        return mergeBeans(from, to);
      }
    });
  }

  @Test
  public void testCustomMergeBeans() throws Exception {
    final C from = new C();
    final B to = new B();
    doTestMergeBeans(from, to, new Function<Void, B>() {
      @Override
      public B fun(Void aVoid) {
        return mergeBeans(from, to, A.class);
      }
    });
  }

  @Test
  public void testCreateCopy() throws Exception {
    B original = new B("B.foo", "B.bar");

    B copy = createCopy(original);

    assertNotNull(copy);
    assertEquals(original.foo, copy.foo);
    assertEquals(original.bar, copy.bar);
  }

  public static class A {
    public String foo;

    A(){
      this.foo = "default A.foo";
    }

    A(String foo) {
      this.foo = foo;
    }
  }

  public static class C extends A {
    C(){
      super("default C.foo");
    }
  }

  public static class B extends A {
    public String bar;

    B(){
      super("default B.foo");
      this.bar = "default B.foo";
    }

    B(String foo, String bar) {
      super(foo);
      this.bar = bar;
    }
  }
}
