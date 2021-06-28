import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Object o) {
        switch (o) {
            case String s:
                System.out.println();
                break;
            case Integer i:
                System.out.println();
                break;
            default:
                break;
        }
    }
}