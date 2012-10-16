def <warning descr="Method testV is unused">testV</warning>(def variable) {
  def v = "s"
  def m="10"
  def <warning descr="Assignment is not used">x</warning>="10"
  try {
    switch (variable) {
      case 1:
        v = "100"
        break
     case 2:
        break
      default:
        throw new IllegalArgumentException("Wrong")
    }
  } catch (Exception e) {
    throw e
  }
  return v + m
}