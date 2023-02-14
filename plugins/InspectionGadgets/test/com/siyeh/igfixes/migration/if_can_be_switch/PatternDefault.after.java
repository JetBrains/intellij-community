import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Object o) {
        <caret>switch (o) {
            case String s -> System.out.println();
            case Integer i -> System.out.println();
            default -> {
            }
        }
    }
}