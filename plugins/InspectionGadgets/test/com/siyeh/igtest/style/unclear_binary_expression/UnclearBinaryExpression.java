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
import java.util.Calendar;

public class UnclearBinaryExpression {

  void foo() {
    boolean b2 = <warning descr="Expression could use clarifying parentheses">"asdf" + "asdf" instanceof String</warning>;
    int i = <warning descr="Expression could use clarifying parentheses">true ? 1 + 2 * 7 : 2</warning>;
    boolean j = true ? false : true;
    System.out.println(<warning descr="Expression could use clarifying parentheses">3 + 1 + 2 * 9 * 8 + 1</warning>);
  }

  boolean bar(String name, Condition condition, Operation operation) {
    List<String> values = new ArrayList();
    return <warning descr="Expression could use clarifying parentheses">name.equals(condition.name)
           && values.contains(condition.value) == (operation == Operation.equals)
           && name instanceof String</warning>;
  }

  class Condition {
    String name;
    String value;
  }
  static class Operation {
    static Operation equals;
  }

  void more(Object o) {
    String s = <warning descr="Expression could use clarifying parentheses">true? false ? "one" : "two" : "three"</warning>;
    boolean b = <warning descr="Expression could use clarifying parentheses">true ? o instanceof String : false</warning>;
  }

  void more(int i) {
    <warning descr="Expression could use clarifying parentheses">i = i += i = 1</warning>;
    int j;
    int k = <warning descr="Expression could use clarifying parentheses">j = 3 / 9 + 5 * 4</warning>;
  }

  void noMore() {
    String s;
    s = ":asdf" + "5s";
  }

  int incomplete(String s) {
    return s == null ?<EOLError descr="Expression expected"></EOLError><EOLError descr="';' expected"></EOLError>
  }
}
class NoWarnings {
  String targetClass;
  String targetBeanName;

  @Override
  public int hashCode() {
    int result = targetClass.hashCode();
    result = (31 * result) + ((targetBeanName != null) ? targetBeanName.hashCode() : 0);
    return result;
  }

  void f(int finalKey) {
    String result = "???" + finalKey + "???";
  }

  private static final int YEAR_MONTHS = 12;
  private static final int YEAR_DAYS = 365;
  void g(Calendar startCalendar, Calendar endCalendar) {
    double result = endCalendar.get(Calendar.YEAR) - startCalendar.get(Calendar.YEAR);
    result += (endCalendar.get(Calendar.MONTH) - startCalendar.get(Calendar.MONTH)) / YEAR_MONTHS;
    result += (endCalendar.get(Calendar.DAY_OF_MONTH) - startCalendar.get(Calendar.DAY_OF_MONTH)) / YEAR_DAYS;
  }
}