import java.util.List;
class Test2 {

    interface I<X> {
        X foo(List<X> list);
    }

    static <T> I<T> bar(I<T> i){return i;}
 
    {
        bar(new I<String>() {
            @Override
            public String foo(List<String> list) {
                <selection>return "sss";</selection>
            }
        });
    }
}
