/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.igtest.bugs.mismatched_string_builder_query_update;

import java.util.*;

public class MismatchedStringBuilderQueryUpdate {

  void foo() {
    final StringBuilder b = new StringBuilder();
    b.append("");
    System.out.println("" + b + "");

    final StringBuilder <warning descr="Contents of StringBuilder 'c' are updated, but never queried">c</warning> = new StringBuilder();
    c.append(' ');
  }

  private static CharSequence getSomething()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("abc");
    return sb.reverse();
  }

  void indexedList(List<String> list) {
    StringBuilder stringBuilder = new StringBuilder(); // <--- false warning here
    list.forEach(stringBuilder::append);
    System.out.println(stringBuilder.toString());
  }

  public static void main(String[] args) {
    Map<String, StringBuilder> map = Collections.singletonMap("buf", new StringBuilder());
    test(map);
    System.out.println(map.get("buf"));
  }

  private static void test(Map<String, StringBuilder> map) {
    // should not be marked as builder is obtained from outside IDEA-162479
    StringBuilder builder = map.get("buf");
    builder.append("xyz");
  }

  static String testUselessQuery(java.util.List<String> list) {
    StringBuilder <warning descr="Contents of StringBuilder 'sb' are updated, but never queried">sb</warning> = new StringBuilder();
    for (String s : list) {
      if (s != null) {
        if (sb.length() > 0) {
          sb.append(',');
        } else {
          sb.append("start");
        }
        sb.append(s.length());
      }
    }
    return null;
  }

  static String testQueryWithSideEffect(java.util.List<String> list) {
    StringBuilder sb = new StringBuilder();
    for (String s : list) {
      if (s != null) {
        if (sb.length() > 0) {
          sb.append(',');
          System.out.println("Hello");
        }
        sb.append(s.length());
      }
    }
    return null;
  }
}
interface List<T> {
  default void forEach(Consumer<? super T> action) {
  }
}
interface Consumer<T> {
  void accept(T t);
}
class EnumConstant {
  private static final StringBuilder sb = new StringBuilder();
  static {
    sb.append("");
  }

  enum SomeEnum {
    ITEM(sb); // passed as argument

    SomeEnum(StringBuilder sb) {}
  }
}
