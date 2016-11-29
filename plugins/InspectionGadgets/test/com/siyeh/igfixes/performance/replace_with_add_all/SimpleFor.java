import java.util.*;

class T {
    void f(Set<String> t, String[] f) {
        <caret>for (int i = 0; i < f.length; i++) {
            t.add(f[i]);
        }
    }
}