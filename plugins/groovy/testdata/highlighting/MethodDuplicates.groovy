def <error descr="foo(String, int) already defined">foo</error>(String s = "a", int i, double y = 4) {

}

def foo(int i, double y) {}

def <error descr="foo(String, int) already defined">foo</error>(String s, int i) {}

def foo(String s, double y){}