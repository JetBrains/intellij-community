import java.util.concurrent.ThreadLocalRandom;

public interface Test {
  int foo();
  Test s2 = Test::bar;

  static int bar() {
  }
}