interface Action {
    void run();

    default String label() {
        return "label";
    }

    static Action noop() {
        return () -> {
        };
    }
}
