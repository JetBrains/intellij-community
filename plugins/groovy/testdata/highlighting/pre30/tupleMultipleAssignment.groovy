interface I {
  Tuple tuple()
  //Tuple0 tuple0()
  Tuple1<String> tuple1()
  Tuple2<Double, Long> tuple2()
  Tuple3<Runnable, Number, Throwable> tuple3()
  Tuple4<Float, Integer, Exception, Thread> tuple4()
}

@groovy.transform.CompileStatic
class Usage {

  void ok(I i) {
    def (s) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple1()</error>
    def (d, l) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple2()</error>
    def (r, n, t) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple3()</error>
    (s) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple1()</error>
    (d, l) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple2()</error>
    (r, n, t) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple3()</error>
  }

  void tooManyComponents(I i) {
    def (s) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple2()</error>
    def (d, l) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple3()</error>
    def (r, n, t) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple4()</error>
    (s) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple2()</error>
    (d, l) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple3()</error>
    (r, n, t) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple4()</error>
  }

  void notEnoughComponents(I i) {
    //def (s) = i.tuple0()
    def (d, l) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple1()</error>
    def (r, n, t) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple2()</error>
    //(s) = i.tuple0()
    (d, l) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple1()</error>
    (r, n, t) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple2()</error>
  }

  void notATuple(I i) {
    def (t) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple()</error>
    def (s) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">""</error>
    (t) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple()</error>
    (s) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">""</error>
  }

  void wrongTyping(I i) {
    def (int s) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple1()</error>
    (s) = <error descr="Multiple assignments without list expressions on the right hand side are unsupported in static type checking mode">i.tuple1()</error>
  }
}
