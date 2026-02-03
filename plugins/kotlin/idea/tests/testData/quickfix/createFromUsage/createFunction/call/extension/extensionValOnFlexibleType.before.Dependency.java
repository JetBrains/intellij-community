interface FooBar {
    void fooBar(String s);

    class Instance {
        public static final FooBar FOO_BAR = new FooBar() {
            @Override
            public void fooBar(String s) {

            }
        };
    }
}