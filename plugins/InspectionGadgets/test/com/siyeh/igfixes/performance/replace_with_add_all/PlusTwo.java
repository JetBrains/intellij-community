import java.util.*;

class Vector {
    private final List<Number> values;

    public Vector(Number... args) {
        values = new ArrayList<>(args.length + 1);
        values.add(null);
        <caret>for (int i = 2; i <= args.length + 1; ++i) {
            values.add(args[i - 2]);
        }
    }
}
