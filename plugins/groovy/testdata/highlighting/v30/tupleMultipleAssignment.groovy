interface I {
  Tuple tuple()
  Tuple0 tuple0()
  Tuple1<String> tuple1()
  Tuple2<Double, Long> tuple2()
  Tuple3<Runnable, Number, Throwable> tuple3()
  Tuple4<Float, Integer, Exception, Thread> tuple4()
}

@groovy.transform.CompileStatic
class Usage {

  void ok(I i) {
    def (s) = i.tuple1()
    def (d, l) = i.tuple2()
    def (r, n, t) = i.tuple3()
    (s) = i.tuple1()
    (d, l) = i.tuple2()
    (r, n, t) = i.tuple3()
  }

  void tooManyComponents(I i) {
    def (s) = i.tuple2()
    def (d, l) = i.tuple3()
    def (r, n, t) = i.tuple4()
    (s) = i.tuple2()
    (d, l) = i.tuple3()
    (r, n, t) = i.tuple4()
  }

  void notEnoughComponents(I i) {
    def (s) = <error descr="Incorrect number of values. Expected:1 Was:0">i.tuple0()</error>
    def (d, l) = <error descr="Incorrect number of values. Expected:2 Was:1">i.tuple1()</error>
    def (r, n, t) = <error descr="Incorrect number of values. Expected:3 Was:2">i.tuple2()</error>
    (s) = <error descr="Incorrect number of values. Expected:1 Was:0">i.tuple0()</error>
    (d, l) = <error descr="Incorrect number of values. Expected:2 Was:1">i.tuple1()</error>
    (r, n, t) = <error descr="Incorrect number of values. Expected:3 Was:2">i.tuple2()</error>
  }

  void notATuple(I i) {
    def (t) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple()</error>
    def (s) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">""</error>
    (t) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple()</error>
    (s) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">""</error>
  }

  void wrongTyping(I i) {
    def (int <error descr="Cannot assign 'String' to 'int'">s</error>) = i.tuple1()
    (<error descr="Cannot assign 'String' to 'int'">s</error>) = i.tuple1()
  }
}
