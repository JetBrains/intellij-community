import org.jetbrains.annotations.Nullable;

public class A {
    @Nullable
    String foo;

    A(@Nullable String foo) {
        this.foo = foo;
    }
}