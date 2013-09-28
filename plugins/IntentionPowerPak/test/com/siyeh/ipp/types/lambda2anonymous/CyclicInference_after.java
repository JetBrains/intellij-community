class Test2 {
    class Y<T>{}
    
    interface I<X> {
        X foo(Y<X> list);
    }

    static <T> I<T> bar(I<T> i){return i;}

    {
        bar(new I() {
            @Override
            public Object foo(Y x) {
                <selection>return x;</selection>
            }
        });
    }
}