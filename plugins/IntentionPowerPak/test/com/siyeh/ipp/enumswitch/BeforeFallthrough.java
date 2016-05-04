class BeforeDefault {
    enum Status { ACTIVE, INACTIVE, ERROR }

    private int foo (Status status) {
        switch (status)<caret> {
            case ACTIVE:
                return 0;
            case INACTIVE:
            default:
                return 1;
        }
    }
}