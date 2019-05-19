enum Test {
    CHECKIN(<warning descr="Hard coded string literal: \"Text1\"">"Text1"</warning>),
    ADD(<warning descr="Hard coded string literal: \"Rext2\"">"Rext2"</warning>);

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