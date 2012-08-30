class Foo extends Inner implements <error descr="Cannot resolve symbol 'Unknown'">Unknown</error> {
  static class Inner {}
}