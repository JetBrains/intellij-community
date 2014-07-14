<error descr="Cyclic inheritance involving 'A'"><error descr="Method 'invokeMethod' is not implemented">class A extends C</error></error> {

}

<error descr="Cyclic inheritance involving 'B'"><error descr="Method 'invokeMethod' is not implemented">class B extends A</error></error> {

}

<error descr="Cyclic inheritance involving 'C'"><error descr="Method 'invokeMethod' is not implemented">class C extends B</error></error> {

}

<error descr="Cyclic inheritance involving 'B'"><error descr="Method 'invokeMethod' is not implemented">class D extends B</error></error> {

}