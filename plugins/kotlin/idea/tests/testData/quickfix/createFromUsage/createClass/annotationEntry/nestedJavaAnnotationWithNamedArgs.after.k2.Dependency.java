import org.jetbrains.annotations.NotNull;

class J {

    public @interface foo {
        int count();

        @NotNull String name();
    }
}
