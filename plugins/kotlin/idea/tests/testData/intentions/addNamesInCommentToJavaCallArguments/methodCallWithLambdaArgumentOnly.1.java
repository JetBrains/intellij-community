public class Java {
    @FunctionalInterface
    public interface I {
        void execute();
    }

    public void test(I i) {}
}