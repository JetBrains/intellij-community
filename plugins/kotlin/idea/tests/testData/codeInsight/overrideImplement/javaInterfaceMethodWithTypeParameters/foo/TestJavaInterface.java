package foo;

public interface TestJavaInterface<K> {
    <T extends K> T onTypingEvent();
}