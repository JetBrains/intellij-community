package test;

public final class JavaFailureHandler {
    public static void consume(Throwable t) {
        throw new RuntimeException(t);
    }
}
