public class ImplementAnonymously {
    void test() {
        MyInterface m = new MyInterface() {
            @Override
            public <T> void myFun(T param) {
                T param2 = param;
            }
        };

    }
}