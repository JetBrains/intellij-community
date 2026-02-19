package one.two;

public class WriteWithInstance {
    public static void main(String[] args) {
        KotlinClass.Named.setStaticLateinit(new KotlinClass());
    }
}