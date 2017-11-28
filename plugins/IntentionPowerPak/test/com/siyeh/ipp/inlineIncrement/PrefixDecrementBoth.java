public class Main {
    public void foo() {
        int i = 3;
        System.out.println(i);
        --<caret>i;
        System.out.println(i);
    }
}