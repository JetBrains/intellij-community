class Test1 {
    class Inner {}
        void test() {
        BlahBlah6 blahBlah62 = Inner:<caret>:new;
        }
}

interface BlahBlah6 {
    Test1.Inner foo6(Test1 p);
}