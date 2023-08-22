import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nls;
import java.util.function.Supplier;

class MyTest {
  static <T> T withLock(Supplier<T> fn) {
    synchronized (fn) {
      return fn.get();
    }
  }
  
  void test(String s) {
    consume(withLock(() -> <warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>));
    consumeOk(withLock(() -> "foo"));
  }
  
  void consume(@Nls String s) {}
  void consumeOk(@NonNls String s) {}
}