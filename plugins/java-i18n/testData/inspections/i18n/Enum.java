import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

enum Test {
    CHECKIN(<warning descr="Hardcoded string literal: \"Text1\"">"Text1"</warning>),
    ADD(<warning descr="Hardcoded string literal: \"Rext2\"">"Rext2"</warning>);

    Test(final String id) {
        myId = id;
    }

    private final String myId;
}

enum Test2 {
    CHECKIN("Text1"),
    ADD("Rext2");

    Test2(@org.jetbrains.annotations.NonNls final String id) {
        myId = id;
    }

    private final String myId;
}
enum Test3 {
    FOO1("bar", <warning descr="Hardcoded string literal: \"baz\"">"baz"</warning>),
    FOO2(("bar"), (<warning descr="Hardcoded string literal: \"baz\"">"baz"</warning>)),
    FOO3(("bar")),
    FOO4("bar");

    Test3(@NotNull @PropertyKey(resourceBundle = "") String s1, @NotNull String s2) {}
    Test3(@NotNull @PropertyKey(resourceBundle = "") String s1) {}
}