import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface A {

    @Nullable Integer foo(int i, @NotNull String string);
}
