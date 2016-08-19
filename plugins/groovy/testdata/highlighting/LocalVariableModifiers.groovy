def foo() {
  def a0 = 1
  <error descr="Variable cannot have modifier 'protected'">protected</error> a1 = 1
  <error descr="Variable cannot have modifier 'private'">private</error> a2 = 1
  <error descr="Variable cannot have modifier 'static'">static</error> a3 = 1
  <error descr="Variable cannot have modifier 'abstract'">abstract</error> a4 = 1
  final a5 = 1
  <error descr="Variable cannot have modifier 'native'">native</error> a6 = 1
  <error descr="Variable cannot have modifier 'synchronized'">synchronized</error> a7 = 1
  <error descr="Variable cannot have modifier 'strictfp'">strictfp</error> a8 = 1
  <error descr="Variable cannot have modifier 'transient'">transient</error> a9 = 1
  <error descr="Variable cannot have modifier 'volatile'">volatile</error> a10 = 1
}
