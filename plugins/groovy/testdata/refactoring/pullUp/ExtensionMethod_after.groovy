interface Base {
    default void foo() {
        System.out.println("Hi there.");
    }
}

interface I2 extends Base {
}
