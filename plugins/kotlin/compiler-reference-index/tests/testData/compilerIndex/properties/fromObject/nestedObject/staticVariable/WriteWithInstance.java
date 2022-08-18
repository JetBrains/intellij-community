package one.two;

public class WriteWithInstance {
    public static void main(String[] args) {
        KotlinObject.Nested.INSTANCE.setStaticVariable(3);
    }
}