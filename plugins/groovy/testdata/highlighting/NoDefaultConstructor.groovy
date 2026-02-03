class X {
    X(int a) {}
}

class Y extends X {

    <error descr="There is no default constructor available in class 'X'">Y()</error> {

    }
}

