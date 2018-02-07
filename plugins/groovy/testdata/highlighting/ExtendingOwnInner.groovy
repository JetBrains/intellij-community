class Foo extends <error descr="Cannot resolve symbol 'Inner'">Inner</error> implements <error descr="Cannot resolve symbol 'Unknown'">Unknown</error> {
  static class Inner {}
}