def args = ["one", "two", "three"]
def (one, two, three) = args
one.toString()
def foo
<warning descr="Variable 'foo' might not be assigned">foo</warning>.toString()

(args, foo) = [2, 3]
args.toString()
foo.toString()