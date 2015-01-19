/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package pkg;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.Arrays;
import java.util.function.IntBinaryOperator;
import java.util.function.Supplier;

public class TestClassLambda {

  public int field = 0;

  public void testLambda() {
    List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6, 7);
    int b = (int)Math.random();

    list.forEach(n -> {
      int a = 2 * n;
      System.out.println(a + b + field);
    });
  }

  public void testLambda1() {
    int a = (int)Math.random();
    Runnable r1 = () -> { System.out.println("hello1" + a); };
    Runnable r2 = () -> { System.out.println("hello2" + a); };
  }

  public void testLambda2() {
    reduce((left, right) -> Math.max(left, right));
  }

  public void testLambda3() { // IDEA-127301
    reduce(Math::max);
  }

  public void testLambda4() {
    reduce(TestClassLambda::localMax);
  }

  public void testLambda5() {
    String x = "abcd";
    function(x::toString);
  }

  public void testLambda6() {
    List<String> list = new ArrayList<String>();
    int bottom = list.size() * 2;
    int top = list.size() * 5;
    list.removeIf(s -> (bottom >= s.length() && s.length() <= top));
  }

  public static void testLambda7(Annotation[] annotations) {
    Arrays.stream(annotations).map(Annotation::annotationType);
  }

  public static OptionalInt reduce(IntBinaryOperator op) {
    return null;
  }

  public static String function(Supplier<String> supplier) {
    return supplier.get();
  }

  public static int localMax(int first, int second) {
    return 0;
  }

  public void nestedLambdas() {
    int a =5;
    Runnable r1 = () -> {
      Runnable r2 = () -> { System.out.println("hello2" + a); };
      System.out.println("hello1" + a);
    };
  }
}
