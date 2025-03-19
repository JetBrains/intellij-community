import junit.framework.TestCase;

public class CommentsAndParenthesesTest extends TestCase {
  public void test() {
    CommentsAndParentheses o = new CommentsAndParentheses();

    o.extraBrackets(false);
    o.extraBracketsExpression(true, false);
    o.extraComment(false);
    o.extraCommentInsideExpression(true, false);
    o.extraBracketsInIf(true);
    o.commentInSwitch(1);
    o.commentInSwitchExpression(1, 0);
    o.extraBracketsInSwitch(1);
  }

  public void testKotlin() {
    KtCommentsAndParentheses o = new KtCommentsAndParentheses();

    o.extraBrackets(false);
    o.extraBracketsExpression(true, false);
    o.extraComment(false);
    o.extraCommentInsideExpression(true, false);
    o.extraBracketsInIf(true);
    o.commentInSwitch(1);
    o.commentInSwitchExpression(1, 0);
    o.extraBracketsInSwitch(1);
  }
}