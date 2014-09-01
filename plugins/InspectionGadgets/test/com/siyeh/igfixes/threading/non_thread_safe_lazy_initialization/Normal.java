public class Normal {
    private static Object example;

    public static Object getInstance() {
        if (example == null) {
            example<caret> = new Object();
        }
        return example
    }
}