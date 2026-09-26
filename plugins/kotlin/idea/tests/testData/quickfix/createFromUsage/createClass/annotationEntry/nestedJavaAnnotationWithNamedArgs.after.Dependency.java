import org.jetbrains.annotations.NotNull;

class J {

    public static @interface foo {
        int count();

        @NotNull String name();
    }
}
