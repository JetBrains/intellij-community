import org.junit.jupiter.api.*;

class WithRepeated {
  @RepeatedTest(<warning descr="The number of repetitions must be greater than zero">-1</warning>)
  void repeatedTestNegative() { }

  @RepeatedTest(<warning descr="The number of repetitions must be greater than zero">0</warning>)
  void repeatedTestBoundaryZerro() { }
}
