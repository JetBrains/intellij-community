import org.jetbrains.annotations.NotNull;

import java.util.Set;

class Foo {
    void bar(int n, String s, @NotNull Set<@NotNull String> of) {}
}