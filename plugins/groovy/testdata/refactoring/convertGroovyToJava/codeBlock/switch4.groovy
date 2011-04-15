def date = new Date(2011, 04, 09)

switch (date) {
  case new Date(20, 11, 23):
    print "aaa"
  case [1]:
    print "bbb"
    break
  default:
    print "ccc"
}
