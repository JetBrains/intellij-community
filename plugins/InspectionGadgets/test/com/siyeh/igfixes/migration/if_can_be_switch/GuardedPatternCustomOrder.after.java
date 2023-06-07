import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Object o, int variable) {
        <caret>switch (o) {
            case String s && !s.isEmpty() -> System.out.println();
            case Integer x && x > 0 && x < 10 -> System.out.println();
            case Float v && variable > 0 -> {
            }
            default -> {
            }
        }
    }
}