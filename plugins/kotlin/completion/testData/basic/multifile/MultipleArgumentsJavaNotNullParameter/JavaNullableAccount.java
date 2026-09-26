import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class JavaNullableAccount {
    JavaNullableAccount(@Nullable String email, @NotNull String password, @Nullable Integer flags) {
    }

    void update(@Nullable String email, @NotNull String password, @Nullable Integer flags) {
    }
}
