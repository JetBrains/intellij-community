public class Java<T> extends Outer<T> {
    private class C extends Outer<T>.Inner<T> {
    }

    private Outer<T>.Inner<T> i;
}
