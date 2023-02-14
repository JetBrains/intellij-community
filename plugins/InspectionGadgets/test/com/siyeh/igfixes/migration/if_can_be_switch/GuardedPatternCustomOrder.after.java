import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Object o, int variable) {
        <caret>switch (o) {
            case String s && !s.isEmpty():
                System.out.println();
                break;
            case Integer x && x > 0 && x < 10:
                System.out.println();
                break;
            case Float aFloat && variable > 0:
                break;
            default:
                break;
        }
    }
}