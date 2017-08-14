public class OctalRecognition {
  public void f() {
    int i1 = <warning descr="Octal integer '0123'">0123</warning>;
    int i2 = <warning descr="Octal integer '0_123'">0_123</warning>;

    int i3 = 0;
    int i4 = 7;
    int i5 = 0x0123;
    int i6 = 0b0101;
  }
}