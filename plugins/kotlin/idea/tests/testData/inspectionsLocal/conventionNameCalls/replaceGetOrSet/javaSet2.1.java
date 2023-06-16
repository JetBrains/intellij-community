public class Foo {
    private int value;

    public final void set(int newValue) {
        value = newValue;
    }

    public final void set(int newValue, int oldValue) {
        value = newValue;
    }

    public final String get(String s) {
        return s;
    }
}
