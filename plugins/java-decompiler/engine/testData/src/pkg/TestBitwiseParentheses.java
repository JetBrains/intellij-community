package pkg;

public class TestBitwiseParentheses {

  public static void main(String[] args) {
    System.out.println(bitwiseAndWithAdd(0xFF, 3, 5));
    System.out.println(bitwiseOrWithMul(0x0F, 3, 5));
    System.out.println(bitwiseXorWithSub(0xFF, 10, 3));
    System.out.println(shiftRightWithSub(0xFF00, 8, 4));
    System.out.println(shiftLeftWithAdd(1, 2, 3));
    System.out.println(unsignedShiftWithMul(-1, 2, 4));
    System.out.println(addInsideBitwiseLeft(3, 5, 0xFF));
    System.out.println(mulInsideShiftLeft(3, 2, 4));
    System.out.println(complexMixed(0xFF, 0xFF00, 8, 4));

    System.out.println(pureArithmetic(10, 3, 5));
    System.out.println(pureBitwiseSame(0xFF, 0x0F, 0xF0));
    System.out.println(pureBitwiseDifferentPrec(0xFF, 0x0F, 0x01));
    System.out.println(bitwiseChildInsideArithmetic(0x0F, 0x03, 10));
    System.out.println(shiftChildInsideArithmetic(8, 2, 5));
  }

  static int bitwiseAndWithAdd(int a, int b, int c) {
    return a & (b + c);
  }

  static int bitwiseOrWithMul(int a, int b, int c) {
    return a | (b * c);
  }

  static int bitwiseXorWithSub(int a, int b, int c) {
    return a ^ (b - c);
  }

  static int shiftRightWithSub(int a, int b, int c) {
    return a >> (b - c);
  }

  static int shiftLeftWithAdd(int a, int b, int c) {
    return a << (b + c);
  }

  static int unsignedShiftWithMul(int a, int b, int c) {
    return a >>> (b * c);
  }

  static int addInsideBitwiseLeft(int a, int b, int c) {
    return (a + b) & c;
  }

  static int mulInsideShiftLeft(int a, int b, int c) {
    return (a * b) << c;
  }

  static int complexMixed(int mask, int value, int total, int bits) {
    return mask & (value >> (total - bits));
  }

  static int pureArithmetic(int a, int b, int c) {
    return a + b * c;
  }

  static int pureBitwiseSame(int a, int b, int c) {
    return a & b & c;
  }

  static int pureBitwiseDifferentPrec(int a, int b, int c) {
    return a | (b & c);
  }

  static int bitwiseChildInsideArithmetic(int a, int b, int c) {
    return (a & b) + c;
  }

  static int shiftChildInsideArithmetic(int a, int b, int c) {
    return (a << b) + c;
  }
}
