import org.jetbrains.annotations.Nullable;

class Test {
    void test(@Nullable Object o) {
        switch (o) {
            case String s -> System.out.println();
            case Integer i -> System.out.println();
            case null -> {
            }
            default -> {
            }
        }
    }
}