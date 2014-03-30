class Test2 {

    class A {
      void foo(){}
    }

    interface I<X> {
        X foo();
    }

    static <T> I<T> bar(I<T> i){return i;}
 
    {
        bar(new I<String>() {
            @Override
            public String foo() {
                <selection>A a = new A() {
                    void foo() {
                        super.foo();
                    }
                }
                return "sss";</selection>
            }
        });
    }
}
