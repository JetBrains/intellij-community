public class Normal {

    private static class ExampleHolder {
        private static final Object example = new Object();
    }

    public static Object getInstance() {
        return ExampleHolder.example
    }
}