package one.two;

public class UsageWithInstance {
    void t() {
        KotlinObject.NestedObject.INSTANCE.overloadsStaticExtension(new Receiver(), 4);
    }
}