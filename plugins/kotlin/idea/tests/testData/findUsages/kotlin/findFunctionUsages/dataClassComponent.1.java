class J1<T extends KotlinDataClass> {
    void foo(T t1) {
        int i1 = t1.component1();
        String s1 = t1.component2();
    }
}