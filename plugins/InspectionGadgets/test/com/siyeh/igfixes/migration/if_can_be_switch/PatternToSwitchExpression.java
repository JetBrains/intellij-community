import org.jetbrains.annotations.NotNull;

class Test {
    void test(@Nullable Object o) {
        <caret>if (o instanceof String s || o == null) {
            System.out.println("nullable string");
        } else if (o instanceof Integer) {
              System.out.println("int");
        } else {
            System.out.println("default");
        }
    }
}