class CC {
  def <warning descr="Method name contains illegal character(s): '.'">'.'</warning>() {}
  def <warning descr="Method name contains illegal character(s): '.'">'duplicate chars ..'</warning>() {}
  def <warning descr="Method name contains illegal character(s): ';'">';'</warning>() {}
  def <warning descr="Method name contains illegal character(s): '['">'['</warning>() {}
  def <warning descr="Method name contains illegal character(s): '/'">'/'</warning>() {}
  def <warning descr="Method name contains illegal character(s): '<'">'<'</warning>() {}
  def <warning descr="Method name contains illegal character(s): '>'">'>'</warning>() {}
  def ':'() {}
  def <warning descr="Method name contains illegal character(s): '.'">'different:chars.'</warning>() {}
}

class CU {
  def <warning descr="Method name contains illegal character(s): '.'">'\u002e'</warning>() {}
  def <warning descr="Method name contains illegal character(s): ';'">'\u003b'</warning>() {}
  def <warning descr="Method name contains illegal character(s): '['">'\u005b'</warning>() {}
  def <warning descr="Method name contains illegal character(s): '/'">'\u002f'</warning>() {}
  def <warning descr="Method name contains illegal character(s): '<'">'\u003c'</warning>() {}
  def <warning descr="Method name contains illegal character(s): '>'">'\u003e'</warning>() {}
  def '\u003a'() {}
}
