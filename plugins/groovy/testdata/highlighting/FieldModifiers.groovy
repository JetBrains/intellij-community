class Simple {
  def a0 = 1
  protected a1 = 1
  private a2 = 1
  static a3 = 1
  abstract a4 = 1
  final a5 = 1
  native a6 = 1
  synchronized a7 = 1
  strictfp a8 = 1
  transient a9 = 1
  volatile a10 = 1
}

class Combinations {
  <error descr="Illegal combination of modifiers">public private</error> a
  <error descr="Illegal combination of modifiers">private protected</error> b
  <error descr="Illegal combination of modifiers">protected public</error> c
  <error descr="Illegal combination of modifiers 'volatile' and 'final'">volatile final</error> g
}

class Duplicates {
  <error descr="Duplicate modifier 'public'">public public</error> a
}

interface I {
  <error descr="Interface members are not allowed to be private">private</error> a
  public b
  <error descr="Interface members are not allowed to be protected">protected</error> c
}
