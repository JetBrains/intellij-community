def a = "foo"

if (a) {
  print a
}
else {
  print 'foo foo'
  def list = [1, 2, 3]
  print list ? "full: $list" : 'empty'
}