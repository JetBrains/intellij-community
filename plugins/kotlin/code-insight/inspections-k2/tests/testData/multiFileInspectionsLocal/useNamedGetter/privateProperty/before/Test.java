public class Test {
    public void test(Secret secret) {
        String pw = secret.<caret>component1();
    }
}