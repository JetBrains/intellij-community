public class LambdaParameterNamingConvention {

  void m(int a) {}
  void n(int abcd) {
    F f = (<warning descr="Lambda parameter name 'i' is too short (1 < 2)">i</warning>) -> 10;
    F g = abc -> 12;
  }

  interface F {
    int a(int i);
  }
}