import java.util.*;

class T {
    void f(Set<String> t, String[] f) {
        <caret>for (int i = 2; i < f.length; i++) {
            String v = f[i];
            t.add(v);
        }
    }
}