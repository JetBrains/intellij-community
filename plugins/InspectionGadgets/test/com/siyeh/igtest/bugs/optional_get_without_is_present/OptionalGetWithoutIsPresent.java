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
import java.util.Optional;

class OptionalWithoutIsPresent {

  void m(Optional<Integer> maybe) {
    if (!!!maybe.isPresent()) {
      maybe = getIntegerOptional();
    }
    else {
      System.out.println(maybe.get());
      maybe = getIntegerOptional();
    }
    if (maybe.isPresent()) {
      maybe = Optional.empty();
      System.out.println(maybe.<warning descr="'Optional.get()' will definitely fail as Optional is empty here">get</warning>());
    }
    boolean b = ((maybe.isPresent())) && maybe.get() == 1;
    boolean c = (!maybe.isPresent()) || maybe.get() == 1;
    Integer value = !maybe.isPresent() ? 0 : maybe.get();
  }

  Optional<Integer> getIntegerOptional() {
    return Math.random() > 0.5 ? Optional.of(1) : Optional.empty();
  }

  private static void a() {
    Optional<String> optional = Optional.empty();
    final boolean present = optional.isPresent();
    // optional = Optional.empty();
    if (present) {
      final String string = optional.get();
      System.out.println(string);
    }
  }

  private static void b() {
    Optional<String> optional = Optional.empty();
    final boolean present = optional.isPresent();
    optional = Optional.empty();
    if (present) {
      final String string = optional.get();
      System.out.println(string);
    }
  }

  public void testMultiVars(Optional<String> opt) {
    boolean present = opt.isPresent();
    boolean absent = !present;
    boolean otherAbsent = !!absent;
    if(otherAbsent) {
      System.out.println(opt.<warning descr="'Optional.get()' will definitely fail as Optional is empty here">get</warning>());
    } else {
      System.out.println(opt.get());
    }
  }

  private void checkReassign(Optional<String> a, Optional<String> b) {
    if(a.isPresent()) {
      b = a;
      System.out.println(b.get());
    }
  }

  private void checkReassign2(Optional<String> a, Optional<String> b) {
    if(b.isPresent()) {
      b = a;
      System.out.println(b.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    }
  }

  private void checkAsserts1() {
    Optional<String> o1 = Optional.empty();
    assert o1.isPresent();
    System.out.println(o1.get());
    Optional<String> o2 = Optional.empty();
    org.junit.Assert.assertTrue(o2.isPresent());
    System.out.println(o2.get());
    Optional<String> o3 = Optional.empty();
    org.testng.Assert.assertTrue(o3.isPresent());
    System.out.println(o3.get());
  }

  private void checkAsserts2() {
    Optional<String> o3 = Optional.empty();
    org.testng.Assert.assertTrue(o3.isPresent());
    System.out.println(o3.get());
  }

  private void checkOf(boolean b) {
    System.out.println(Optional.of("xyz").get());
    Optional<String> test;
    if(b) {
      test = Optional.of("x");
    } else {
      test = Optional.of("y");
    }
    System.out.println(test.get());
    if(b) {
      test = Optional.of("x");
    } else {
      test = Optional.empty();
    }
    System.out.println(test.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    if(b) {
      test = Optional.empty();
    } else {
      test = Optional.empty();
    }
    System.out.println(test.<warning descr="'Optional.get()' will definitely fail as Optional is empty here">get</warning>());

  }

  private void checkOfNullable(String value) {
    System.out.println(Optional.ofNullable(value).<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    System.out.println(Optional.ofNullable(value+"a").get());
    System.out.println(Optional.ofNullable("xyz").get());
  }

  public static String demo() {
    Optional<String> holder = Optional.empty();

    if (! holder.isPresent()) {
      holder = Optional.of("hello world");
      if (!holder.isPresent()) {
        return null;
      }
    }

    return holder.get();
  }

  //public Collection<String> findCategories(Long articleId) {
  //  return java.util.stream.Stream.of(Optional.of("asdf")).filter(Optional::isPresent).map(x -> x.get()).collect(
  //    java.util.stream.Collectors.toList()) ;
  //}

  public static void main(String[] args) {
    Optional<String> stringOpt;

    if((stringOpt = getOptional()).isPresent()) {
      stringOpt.get();
    }
  }

  public static void main2(String[] args) {
    if(getOptional().isPresent()) {
      getOptional().get(); //'Optional.get()' without 'isPresent()' check
    }
  }

  public static Optional<String> getOptional() {
    return Optional.empty();
  }

  //void order(Optional<String> order) {
  //  order.ifPresent(o -> System.out.println(order.get()));
  //}

  public static void two(Optional<Object> o1,Optional<Object> o2) {
    if (!o1.isPresent() && !o2.isPresent()) {
      return;
    }
    System.out.println(o1.isPresent() ? o1.get() : o2.get());
  }

  public static void two2(Optional<Object> o1,Optional<Object> o2) {
    if (!o2.isPresent()) {
      return;
    }
    System.out.println(o1.isPresent() ? o1.get() : o2.get());
  }

  public static void two3(Optional<Object> o1,Optional<Object> o2) {
    System.out.println(o1.isPresent() ? o1.get() : o2.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
  }

  void def(Optional<String> def) {
    if (!def.isPresent()) {
      throw new RuntimeException();
    }
    {
      def.get();
    }
  }

  private void orred(Optional<Integer> opt1, Optional<Integer> opt2) {
    if (!opt1.isPresent() || !opt2.isPresent()) {
      return;
    }
    opt1.get();
    opt2.get();
  }

  static final class Range {
    Optional<Integer> min = Optional.of(1);
    Optional<Integer> max = Optional.of(2);

    Optional<Integer> getMax() {
      return max;
    }
    Optional<Integer> getMin() {
      return min;
    }
  }
  void useRange(Range a, Range b) {
    if (!a.getMax().isPresent() || !b.getMin().isPresent()) {

    }
    else if (a.getMax().get() <= b.getMin().get()) {

    }
  }

  public void foo(Optional<Long> value) {
    if (!value.isPresent()) {
      return;
    }

    try {
      System.out.println(value.get()); // <- warning appears here
    } finally {
      System.out.println("hi");
    }
  }

  void shortIf(Optional<String> o) {
    if (true || o.isPresent()) {
      o.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>();
    }
  }

  void test(Optional<String> aOpt, Optional<String> bOpt) {
    if (!aOpt.isPresent() || !bOpt.isPresent()) {
      throw new RuntimeException();
    }
    String a = aOpt.get();
    String b = bOpt.get();
  }

  String f(Optional<String> optional, Optional<String> opt2) {
    return optional.isPresent()  ? opt2.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>() : null;
  }
}