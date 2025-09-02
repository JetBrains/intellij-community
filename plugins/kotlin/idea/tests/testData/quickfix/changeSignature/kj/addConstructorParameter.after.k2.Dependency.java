import org.jetbrains.annotations.NotNull;

import java.util.Set;

class Foo {
    Foo(int n, String s, @NotNull Set<@NotNull String> of) {}
}