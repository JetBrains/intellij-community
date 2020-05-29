
public class SimpleCodeExample {

    public static void main(String[] args) {
        int a = 3;
        <caret>int b = 4;

        if(a > b) {
            System.out.println(a + " > " + b);
        }
    }
}
