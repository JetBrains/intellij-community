def foo(String... args) {}

foo("a", 'b', "c")
foo(["a"] as String[])

def foo(String s = 'a', int x){}

foo('a', 2)
foo(4)