import org.jetbrains.annotations.NotNull;

class Test {
    void test(@Nullable Object o) {
        <caret>switch (o) {
            case String s, null -> System.out.println("nullable string");
            case Integer integer -> System.out.println("int");
            default -> System.out.println("default");
        }
    }
}