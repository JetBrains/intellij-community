class Main {
    enum Status { ACTIVE, INACTIVE, ERROR }

    private void foo (Status status) {
        switch<caret> (status) {
            case ERROR:
                break;
        }
    }
}