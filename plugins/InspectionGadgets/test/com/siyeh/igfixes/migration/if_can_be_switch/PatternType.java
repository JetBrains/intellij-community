import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Object o) {
        <caret>if (o instanceof String) {
            System.out.println();
        } else if (o instanceof Integer) {
            System.out.println();
        } else {
        }
    }
}