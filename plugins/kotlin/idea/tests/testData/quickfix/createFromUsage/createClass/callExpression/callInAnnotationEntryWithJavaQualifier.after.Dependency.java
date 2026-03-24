import org.jetbrains.annotations.NotNull;

class J {

    public @interface bar {
        @NotNull String string();

        int i();
    }
}
