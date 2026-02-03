interface Base {
}

interface I2 extends Base {
    default void foo<caret>() {
        System.out.println("Hi there.");
    }
}
