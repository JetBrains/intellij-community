public class Main {
    int i = 3;

    public void foo() {
        System.out.println(i);
        ++<caret>i;
        System.out.println(i);
    }
}