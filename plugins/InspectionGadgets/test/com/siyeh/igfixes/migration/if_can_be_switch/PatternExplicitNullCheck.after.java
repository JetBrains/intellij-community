import org.jetbrains.annotations.Nullable;

class Test {
    void test(@Nullable Object o) {
        switch (o) {
            case String s -> System.out.println();
            case Integer i, null -> System.out.println();
            default -> {
            }
        }
    }
}