enum E {
  a,
  b(),
  c<error descr="Enum initialization with a map entry expression is not supported before Groovy 2.2">(a:2)</error>,
  d(1),
  e<warning descr="Constructor 'E' in 'E' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(1, 2)</warning>,
  f<warning descr="Constructor 'E' in 'E' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(1, 2)</warning>{}

  def E(int x){}
  def E(){}
}