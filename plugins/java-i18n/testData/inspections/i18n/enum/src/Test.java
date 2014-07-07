enum Test {
    CHECKIN("Text1"),
    ADD("Rext2");

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