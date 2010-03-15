def foo(byte[] b) {}
def bar(byte[] b) {
  foo(b)
  foo<warning descr="'foo' in 'ByteArrayArgument' cannot be applied to '(java.lang.Integer)'">(239)</warning>
}