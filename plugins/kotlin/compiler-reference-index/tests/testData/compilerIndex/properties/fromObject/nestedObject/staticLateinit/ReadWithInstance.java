package one.two;

public class ReadWithInstance {
    public static void main(String[] args) {
        KotlinObject.Nested i = KotlinObject.Nested.INSTANCE.getStaticLateinit();
    }
}