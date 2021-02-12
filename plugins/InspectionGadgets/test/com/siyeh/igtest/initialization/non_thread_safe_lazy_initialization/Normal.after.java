public class Normal {

    private static final class ExampleHolder {
        private /*1*/ static final /*2*/ Object /*3*/ example/*4*/ = new Object();
    }

    public static Object getInstance() {
        // 5
        //6
        //7
        //8
        return ExampleHolder.example;
    }
}