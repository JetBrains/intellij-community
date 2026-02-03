class Test<T> {
  T foo(T t) {t}
}

def test=new Test<String>()
def a = test.&foo
def res = a("a")
print re<ref>s