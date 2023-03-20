package one.two;

public class UsageWithInstance2 {
    void t() {
        KotlinObject.NestedObject.INSTANCE.overloadsStaticExtension(new Receiver());
    }
}