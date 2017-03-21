public class Main {
    public void foo() {
        int i = 3;
        --<caret>i; /* comment */
        System.out.println(i);
    }
}