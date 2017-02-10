import java.util.*;

class Vector {
    private final List<Number> values;

    public Vector(int a, int b, Number... args) {
        values = new ArrayList<>(args.length + 1);
        values.add(null);
        <caret>for (int i = a + 1; i <= b - 1; ++i) {
            values.add(args[i - 1]);
        }
    }
}
