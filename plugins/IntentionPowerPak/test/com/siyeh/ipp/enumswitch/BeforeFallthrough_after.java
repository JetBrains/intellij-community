class BeforeDefault {
    enum Status { ACTIVE, INACTIVE, ERROR }

    private int foo (Status status) {
        switch (status) {
            case ACTIVE:
                return 0;
            case ERROR:
                break;
            case INACTIVE:
            default:
                return 1;
        }
    }
}