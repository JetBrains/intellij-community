public class Ternary {
  private static boolean nullOrRandom1(Double v1, Double v2) {
    return v1 == null || v2 == null ? v1 == v2 : Math.random() > 0.5;
  }

  private static boolean nullOrRandom2(Double v1, Double v2) {
    return v1 == null || v2 == null ? v2 == v1 : Math.random() > 0.5;
  }

  private static boolean nullOrRandom3(Double v1, Double v2) {
    return v2 == null || v1 == null ? v1 == v2 : Math.random() > 0.5;
  }

  private static boolean nullOrRandom4(Double v1, Double v2) {
    return v2 == null || v1 == null ? v2 == v1 : Math.random() > 0.5;
  }

  private static boolean nullOrRandom5(Double v1, Double v2) {
    return v1 != null && v2 != null ? Math.random() > 0.5 : v1 == v2;
  }

  private static boolean nullOrRandom6(Double v1, Double v2) {
    return v1 != null && v2 != null ? Math.random() > 0.5 : v2 == v1;
  }

  private static boolean nullOrRandom7(Double v1, Double v2) {
    return v2 != null && v1 != null ? Math.random() > 0.5 : v1 == v2;
  }

  private static boolean nullOrRandom8(Double v1, Double v2) {
    return v2 != null && v1 != null ? Math.random() > 0.5 : v2 == v1;
  }

  private static boolean nullOrRandom9(Double v1, Double v2) {
    return !(v1 != null && v2 != null) ? v1 == v2 : Math.random() > 0.5;
  }

  private static boolean nullOrRandom10(Double v1, Double v2) {
    return !(v1 != null && v2 != null) ? v2 == v1 : Math.random() > 0.5;
  }

  private static boolean nullOrRandom11(Double v1, Double v2) {
    return !(v2 != null && v1 != null) ? v1 == v2 : Math.random() > 0.5;
  }

  private static boolean nullOrRandom12(Double v1, Double v2) {
    return !(v2 != null && v1 != null) ? v2 == v1 : Math.random() > 0.5;
  }

  private static boolean nullOrRandom13(Double v1, Double v2) {
    return !(v1 == null || v2 == null) ? Math.random() > 0.5 : v1 == v2;
  }

  private static boolean nullOrRandom14(Double v1, Double v2) {
    return !(v1 == null || v2 == null) ? Math.random() > 0.5 : v2 == v1;
  }

  private static boolean nullOrRandom15(Double v1, Double v2) {
    return !(v2 == null || v1 == null) ? Math.random() > 0.5 : v1 == v2;
  }

  private static boolean nullOrRandom16(Double v1, Double v2) {
    return !(v2 == null || v1 == null) ? Math.random() > 0.5 : v2 == v1;
  }

  private static boolean nullOrRandom17(Double v1, Double v2) {
    return ((((v1 == null)) || ((v2 == null)))) ? ((v1 == v2)) : Math.random() > 0.5;
  }

  private static boolean nullOrRandom18(Double v1, Double v2) {
    return ((((v1 == null)) || ((v2 == null)))) ? ((v2 == v1)) : Math.random() > 0.5;
  }

  private static boolean nullOrRandom19(Double v1, Double v2) {
    return ((((v2 == null)) || ((v1 == null)))) ? ((v1 == v2)) : Math.random() > 0.5;
  }

  private static boolean nullOrRandom20(Double v1, Double v2) {
    return ((((v2 == null)) || ((v1 == null)))) ? ((v2 == v1)) : Math.random() > 0.5;
  }

  private static boolean nullOrRandom21(Double v1, Double v2) {
    return ((((v1 != null)) && ((v2 != null)))) ? Math.random() > 0.5 : ((v1 == v2));
  }

  private static boolean nullOrRandom22(Double v1, Double v2) {
    return ((((v1 != null)) && ((v2 != null)))) ? Math.random() > 0.5 : ((v2 == v1));
  }

  private static boolean nullOrRandom23(Double v1, Double v2) {
    return ((((v2 != null)) && ((v1 != null)))) ? Math.random() > 0.5 : ((v1 == v2));
  }

  private static boolean nullOrRandom24(Double v1, Double v2) {
    return ((((v2 != null)) && ((v1 != null)))) ? Math.random() > 0.5 : ((v2 == v1));
  }

  private static boolean nullOrRandom25(Double v1, Double v2) {
    return !((((v1 != null)) && ((v2 != null)))) ? ((v1 == v2)) : Math.random() > 0.5;
  }

  private static boolean nullOrRandom26(Double v1, Double v2) {
    return !((((v1 != null)) && ((v2 != null)))) ? ((v2 == v1)) : Math.random() > 0.5;
  }

  private static boolean nullOrRandom27(Double v1, Double v2) {
    return !((((v2 != null)) && ((v1 != null)))) ? ((v1 == v2)) : Math.random() > 0.5;
  }

  private static boolean nullOrRandom28(Double v1, Double v2) {
    return !((((v2 != null)) && ((v1 != null)))) ? ((v2 == v1)) : Math.random() > 0.5;
  }

  private static boolean nullOrRandom29(Double v1, Double v2) {
    return !((((v1 == null)) || ((v2 == null)))) ? Math.random() > 0.5 : ((v1 == v2));
  }

  private static boolean nullOrRandom30(Double v1, Double v2) {
    return !((((v1 == null)) || ((v2 == null)))) ? Math.random() > 0.5 : ((v2 == v1));
  }

  private static boolean nullOrRandom31(Double v1, Double v2) {
    return !((((v2 == null)) || ((v1 == null)))) ? Math.random() > 0.5 : ((v1 == v2));
  }

  private static boolean nullOrRandom32(Double v1, Double v2) {
    return !((((v2 == null)) || ((v1 == null)))) ? Math.random() > 0.5 : ((v2 == v1));
  }
}