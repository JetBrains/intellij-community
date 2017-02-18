import java.util.*;

class T {
  void f(Set<String> t, String[] f, int a, int b) {
        <caret>for (int i = a; i < b; i++) {
      t.add(f[i]);
    }
  }
}