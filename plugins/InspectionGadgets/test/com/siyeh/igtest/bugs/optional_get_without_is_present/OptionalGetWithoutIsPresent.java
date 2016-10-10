import java.util.Optional;

class OptionalWithoutIsPresent {

  void m(Optional<Integer> maybe) {
    if (!!!maybe.isPresent()) {
      maybe = Optional.empty();
    }
    else {
      System.out.println(maybe.get());
      maybe = Optional.empty();
    }
    if (maybe.isPresent()) {
      maybe = Optional.empty();
      System.out.println(maybe.get());
    }
    boolean b = ((maybe.isPresent())) && maybe.get() == 1;
    boolean c = (!maybe.isPresent()) || maybe.get() == 1;
    Integer value = !maybe.isPresent() ? 0 : maybe.get();
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

  private void checkAsserts() {
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

  class Range {
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
}