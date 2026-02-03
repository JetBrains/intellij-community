String s = '';
try {
  print 2
}
catch (UnsupportedOperationException ignored) {
  <warning descr="Assignment is not used">s</warning>=''
  s=''
}
catch (RuntimeException ignored) {
  s = ''
}
catch (Exception ignored) {
  s = ''
}
print s
