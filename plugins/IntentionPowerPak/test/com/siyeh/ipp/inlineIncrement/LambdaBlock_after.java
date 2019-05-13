public class Main {
    public void foo() {
        I i = n -> {
            System.out.println(++n);}
    }
    interface I {int get(int n);}
}