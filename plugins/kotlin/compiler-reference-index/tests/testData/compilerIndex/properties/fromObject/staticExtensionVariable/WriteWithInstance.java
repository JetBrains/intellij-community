package one.two;

public class WriteWithInstance {
    public static void main(String[] args) {
        KotlinObject.INSTANCE.setStaticExtensionVariable(4, 3);
    }
}