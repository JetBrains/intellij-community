<error descr="Method with signature foo(String, int) is already defined in the class 'MethodDuplicates'">def foo(String s = "a", int i, double y = 4)</error> {

}

def foo(int i, double y) {}

<error descr="Method with signature foo(String, int) is already defined in the class 'MethodDuplicates'">def foo(String s, int i)</error> {}

def foo(String s, double y){}
