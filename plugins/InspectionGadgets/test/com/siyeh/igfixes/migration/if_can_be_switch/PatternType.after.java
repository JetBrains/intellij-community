import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Object o) {
        switch (o) {
            case String s -> System.out.println();
            case Integer integer -> System.out.println();
            default -> {
            }
        }
    }
}