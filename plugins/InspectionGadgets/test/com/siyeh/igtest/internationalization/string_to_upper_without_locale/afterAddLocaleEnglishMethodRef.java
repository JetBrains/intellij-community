// "Add 'Locale.ENGLISH' argument" "true"
import java.util.Locale;
import java.util.Optional;

class X {
  void test(Optional<String> opt) {
    String s = opt.map(s1 -> s1.toUpperCase(Locale.ENGLISH)).orElse(null);
  }
}