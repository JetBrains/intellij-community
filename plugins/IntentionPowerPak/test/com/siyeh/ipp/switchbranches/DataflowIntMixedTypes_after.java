class DataflowIntMixedTypes {
  void test(int[] arr) {
    switch<caret> (arr.length % 10) {
        case 0:
            break;
        case 1:
            break;
        case '\u0002': break;
        case 3:
            break;
        case 4:
            break;
        case (byte)(0x105): break;
        case 6:
            break;
        case 7:
            break;
        case (short)(0x100008): break;
        case 9:
            break;
    }
  }
}