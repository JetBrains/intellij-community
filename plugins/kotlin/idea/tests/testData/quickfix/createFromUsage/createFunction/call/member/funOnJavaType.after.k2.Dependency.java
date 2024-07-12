import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class A {

    public @Nullable Integer foo(int i, @NotNull String string) {
        return null;
    }
}
