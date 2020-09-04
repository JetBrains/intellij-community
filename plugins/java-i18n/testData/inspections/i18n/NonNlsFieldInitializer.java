import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

class MyTest {
  MyTest(@Nls String nls) {}

  static final @NonNls MyTest myTest = new MyTest(<warning descr="Hardcoded string literal: \"Hello World!\"">"Hello World!"</warning>);
}