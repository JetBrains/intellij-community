import java.util.HashMap;

public class Foo extends HashMap<Integer, Integer> {
    public int put(int key, int value) {
        return value;
    }
}
