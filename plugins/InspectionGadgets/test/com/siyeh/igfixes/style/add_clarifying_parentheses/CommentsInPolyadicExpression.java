public class CommentsInPolyadicExpression {
  boolean b = true ||<caret>/*c*/ false /*b*/&& // a
                      true;
}