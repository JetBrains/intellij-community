import org.jetbrains.annotations.NotNull;

class J {

    public @interface foo {
        int i();

        @NotNull String string();
    }
}
