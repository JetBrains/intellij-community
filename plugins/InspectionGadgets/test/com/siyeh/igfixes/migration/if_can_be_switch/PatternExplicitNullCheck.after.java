import org.jetbrains.annotations.Nullable;

class Test {
    void test(@Nullable Object o) {
        switch (o) {
            case String s:
                System.out.println();
                break;
            case Integer i:
            case null:
                System.out.println();
                break;
            default:
                break;
        }
    }
}