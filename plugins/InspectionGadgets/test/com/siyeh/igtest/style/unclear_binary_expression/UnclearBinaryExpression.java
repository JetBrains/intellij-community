/*
 * Copyright 2011 Bas Leijdekkers
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
package com.siyeh.igtest.style.unclear_binary_expression;

import java.util.ArrayList;
import java.util.List;

public class UnclearBinaryExpression {

  void foo() {
    boolean b2 = "asdf" + "asdf" instanceof String;
    int i = true ? 1 + 2 * 7 : 2;
    boolean j = true ? false : true;
    System.out.println(3 + 1 + 2 * 9 * 8 + 1);
  }

  boolean bar(String name, Condition condition, Operation operation) {
    List<String> values = new ArrayList();
    return name.equals(condition.name)
           && values.contains(condition.value) == (operation == Operation.equals)
           && name instanceof String;
  }

  class Condition {
    String name;
    String value;
  }
  static class Operation {
    static Operation equals;
  }

  void more(Object o) {
    String s = true? false ? "one" : "two" : "three";
    boolean b = true ? o instanceof String : false;
  }

  void more(int i) {
    i = i += i = 1;
  }

  void noMore() {
    String s;
    s = ":asdf" + "5s";
  }

  int incomplete(String s) {
    return s == null ?
  }
}
