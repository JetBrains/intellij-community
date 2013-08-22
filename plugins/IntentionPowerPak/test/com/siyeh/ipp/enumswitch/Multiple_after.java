class Main {
    enum Status { ACTIVE, INACTIVE, ERROR }

    private void foo (Status status) {
        switch (status) {
            case ACTIVE:
                break;
            case INACTIVE:
                break;
            case ERROR:
                break;
        }
    }
}