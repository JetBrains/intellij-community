interface A {
    default void sample() {
        Thread thread = new Thread(new <warning descr="Anonymous class 'Runnable' may be a named 'static' inner class">Runn<caret>able</warning>() {
            @Override
            public void run() {
            }
        });
    }
}