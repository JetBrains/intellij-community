def foo(String... args) {}

foo("a", 'b', "c")
foo(["a"] as String[])

def foo(String s = 'a', int x){}

def foo(Map map, int x) {}

foo('a', 2)
foo(4)

foo(a:2, 4, c:3)