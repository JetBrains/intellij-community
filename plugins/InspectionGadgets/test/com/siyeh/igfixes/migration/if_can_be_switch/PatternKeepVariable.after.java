import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Object o) {
        int f = 42;
        <caret>switch (o) {
            case String keepName:
                System.out.println();
                break;
            case Integer i:
                System.out.println();
                break;
            case Float f1:
                System.out.println();
                break;
            default:
                break;
        }
        int i = 42;
    }
}