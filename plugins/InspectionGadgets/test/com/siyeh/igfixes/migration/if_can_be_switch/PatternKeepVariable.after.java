import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Object o) {
        int f = 42;
        <caret>switch (o) {
            case String keepName -> System.out.println();
            case Integer i -> System.out.println();
            case Float f1 -> System.out.println();
            default -> {
            }
        }
        int i = 42;
    }
}