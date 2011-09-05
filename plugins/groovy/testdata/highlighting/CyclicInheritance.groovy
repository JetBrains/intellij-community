<error descr="Method 'invokeMethod' is not implemented"><error descr="Cyclic inheritance involving 'A'">class A extends C</error></error> {

}

<error descr="Method 'invokeMethod' is not implemented"><error descr="Cyclic inheritance involving 'B'">class B extends A</error></error> {

}

<error descr="Method 'invokeMethod' is not implemented"><error descr="Cyclic inheritance involving 'C'">class C extends B</error></error> {

}

<error descr="Method 'invokeMethod' is not implemented"><error descr="Cyclic inheritance involving 'B'">class D extends B</error></error> {

}