class ArrayNegativeSize {
  private static final int VAL1 = -10;
  private static int VAL2 = -10;
  private static final int VAL3 = -10 * 2;
  private static final short VAL4 = -10 * 2;

  public static void main(String[] args) {
    final int[] array1 = new int[<warning descr="Allocation of negative (-10) length array">-10</warning>];
    final int[] array2 = createArray();
    final int[] array3 = new int[] {};
    final int[] array4 = new int[<warning descr="Allocation of negative (-90) length array">10 - 100</warning>];
    final int[] array5 = new int[((int) (Integer.valueOf(Integer.MAX_VALUE).longValue() - 100)) + 100 - 10];
    final int[] array6 = new int[((int) (Integer.valueOf(Integer.MAX_VALUE).longValue() - 100)) + 99];
    final int[] array7 = new int[<warning descr="Allocation of negative (-2) length array">(int)(((long) ((Integer.MAX_VALUE - 100)) + 100)*2)</warning>];
    final int[] array8 = new int[num()];
    final int[] array9 = new int[<warning descr="Allocation of negative (-10) length array">VAL1</warning>];
    final int[] array10 = new int[VAL2];
    final int[] array11 = new int[<warning descr="Allocation of negative (-20) length array">VAL3</warning>];
    final int[] array12 = new int[<warning descr="Allocation of negative (-20) length array">VAL4</warning>];
    final int[][] array13 = new int[0][<warning descr="Allocation of negative (-1) length array">-1</warning>];
    final int[][][] array14 = new int[0][0][<warning descr="Allocation of negative (-1) length array">-1</warning>];
    final int[][][] array15 = new int[<warning descr="Allocation of negative (-1) length array">-1</warning>][<warning descr="Allocation of negative (-2) length array">-2</warning>][<warning descr="Allocation of negative (-3) length array">-3</warning>];
    final int[] array16 = new int[<warning descr="Allocation of negative (-7) length array">-07</warning>];
    final int[] array17 = new int[<warning descr="Allocation of negative (-6,300) length array">100 * -077</warning>];
    final int[] array18 = new int[0x7fffffff];
    final int[] array19 = new int[<warning descr="Allocation of negative (-2,147,483,648) length array">0x7fffffff+1</warning>];
    final int[] array20 = new int[0b1111111111111111111111111111111];
    final int[] array21 = new int[<warning descr="Allocation of negative (-2,147,483,648) length array">0b1111111111111111111111111111111+1</warning>];
    action(new int[<warning descr="Allocation of negative (-1,000,000,000) length array">-1000000000</warning>]);
    action(new int[<warning descr="Allocation of negative (-51,966) length array">-0xcafe</warning>]);
    action(new int[<warning descr="Allocation of negative (-1,316,134,912) length array">(int)-10000000000000L</warning>]);
    action(new int[<warning descr="Allocation of negative (-2,147,483,648) length array">2147483647+1</warning>]);
    action(new int["".length()+456]);
    VAL2++; // to remove some inspection warnings
  }

  private static int[] createArray() {
    return new int[0];
  }

  private static void action(Object obj) {
  }

  private static int num() {
    return -123;
  }
}
