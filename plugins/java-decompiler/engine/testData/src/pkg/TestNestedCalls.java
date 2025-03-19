package pkg;

import java.util.function.BiFunction;

public class TestNestedCalls<K, V> {
    public static void main(String[] args) {

    }

    V merge(V a1, BiFunction<? super V, ? super V, ? extends V> func, V a2) {
        Object var5 = a2 == null ? a1 : func.apply(a2, a1);
        if (var5 == null) {
            return a2;
        }
        return (V) var5;
    }
}
