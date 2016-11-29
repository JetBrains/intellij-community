import java.util.*;

class Vector {
    private final List<Number> values;

    public Vector(int a, int b, Number... args) {
        values = new ArrayList<>(args.length + 1);
        values.add(null);
        values.addAll(Arrays.asList(args).subList(a, args.length - b + 2));
    }
}