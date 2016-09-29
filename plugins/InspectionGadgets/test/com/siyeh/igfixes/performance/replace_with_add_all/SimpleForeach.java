import java.util.*;

class T {
    void f(Set<String> t, String[] f) {
        <caret>for (String s : f) {
            t.add(s);
        }
    }
}