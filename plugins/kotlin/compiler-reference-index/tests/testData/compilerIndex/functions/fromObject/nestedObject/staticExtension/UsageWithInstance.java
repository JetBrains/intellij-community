package one.two;

public class UsageWithInstance {
    void t() {
        KotlinObject.NestedObject.INSTANCE.staticExtension(new Receiver(), 4);
    }
}