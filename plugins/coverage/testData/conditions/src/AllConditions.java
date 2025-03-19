public class AllConditions {
  void test1(boolean a, boolean b) {
    int res0 = a && b ? 1 : 2;
    int res1 = a || b ? 1 : 2;
  }

  void test2(boolean a, boolean b, boolean c) {
    int res0 = a && b && c ? 1 : 2;
    int res1 = a || b && c ? 1 : 2;
    int res2 = a && (b || c) ? 1 : 2;
    int res3 = a || b || c ? 1 : 2;
    int res4 = a && b || c ? 1 : 2;
    int res5 = (a || b) && c ? 1 : 2;
  }

  void test3(boolean a, boolean b, boolean c, boolean d) {
    int res0 = a && b && c && d ? 1 : 2;
    int res1 = a || b && c && d ? 1 : 2;
    int res2 = a && (b || c && d) ? 1 : 2;
    int res3 = a || b || c && d ? 1 : 2;
    int res4 = a && b && (c || d) ? 1 : 2;
    int res5 = a || b && (c || d) ? 1 : 2;
    int res6 = a && (b || c || d) ? 1 : 2;
    int res7 = a || b || c || d ? 1 : 2;
    int res8 = a && (b && c || d) ? 1 : 2;
    int res9 = a || b && c || d ? 1 : 2;
    int res10 = a && (b || c) && d ? 1 : 2;
    int res11 = a || (b || c) && d ? 1 : 2;
    int res12 = a && b || c && d ? 1 : 2;
    int res13 = a && b || c || d ? 1 : 2;
    int res14 = (a || b) && c && d ? 1 : 2;
    int res15 = (a || b) && (c || d) ? 1 : 2;
    int res16 = a && b && c || d ? 1 : 2;
    int res17 = (a || b && c) && d ? 1 : 2;
    int res18 = a && (b || c) || d ? 1 : 2;
    int res19 = (a || b || c) && d ? 1 : 2;
    int res20 = (a && b || c) && d ? 1 : 2;
    int res21 = (a || b) && c || d ? 1 : 2;
  }
}
