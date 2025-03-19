public class CommentsAndParentheses {
  String extraBrackets(boolean a) {
    return (a) ? "1" : "2";
  }

  String extraBracketsExpression(boolean a, boolean b) {
    return (a && (b)) ? "1" : "2";
  }

  String extraComment(boolean a) {
    return a /* comment */ ? "1" : "2";
  }

  String extraCommentInsideExpression(boolean a, boolean b) {
    return a /* comment */ && b ? "1" : "2";
  }

  void extraBracketsInIf(boolean a) {
    if ((((((a)))))) {
      System.out.println(1);
    }
    else {
      System.out.println(2);
    }
  }

  void commentInSwitch(int x) {
    switch (/* comment */ x) {
      case 1:
        System.out.println(1);
      default:
        System.out.println(0);
    }
  }

  void commentInSwitchExpression(int x, int y) {
    switch (y + /* comment */ x) {
      case 1:
        System.out.println(1);
      default:
        System.out.println(0);
    }
  }

  void extraBracketsInSwitch(int x) {
    switch ((((((x)))))) {
      case 1:
        System.out.println(1);
      default:
        System.out.println(0);
    }
  }
}
