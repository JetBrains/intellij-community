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
      System.out.println(maybe.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
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
      final String string = optional.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>();
      System.out.println(string);
    }
  }
}