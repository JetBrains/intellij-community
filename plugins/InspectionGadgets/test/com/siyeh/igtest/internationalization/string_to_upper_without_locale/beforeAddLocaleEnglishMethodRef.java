// "Add 'Locale.ENGLISH' argument" "true"
import java.util.Optional;

class X {
  void test(Optional<String> opt) {
    String s = opt.map(String::to<caret>UpperCase).orElse(null);
  }
}