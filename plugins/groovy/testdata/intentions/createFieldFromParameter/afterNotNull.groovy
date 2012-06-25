// "Create Field for Parameter 'name'" "true"

import org.jetbrains.annotations.NotNull;

public class TestBefore {

    @NotNull
    private final String myName;

    public TestBefore(@NotNull String name) {
        super();
        myName = name;
    }
}
