import org.jetbrains.annotations.NotNull;

class J {

    public static @interface foo {
        int i();

        @NotNull String s();
    }
}
