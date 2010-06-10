class <error descr="Cyclic inheritance involving 'A'">A</error> extends C {

}

class <error descr="Cyclic inheritance involving 'B'">B</error> extends A {

}

class <error descr="Cyclic inheritance involving 'C'">C</error> extends B {

}

class <error descr="Cyclic inheritance involving 'B'">D</error> extends B {

}