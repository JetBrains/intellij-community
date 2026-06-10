enum E {
  a,
  b(),
  c(a:2),
  d(1),
  e<warning descr="Constructor 'E' in 'E' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(1, 2)</warning>,
  f<warning descr="Constructor 'E' in 'E' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(1, 2)</warning>{}

  def E(int x){}
  def E(){}
}