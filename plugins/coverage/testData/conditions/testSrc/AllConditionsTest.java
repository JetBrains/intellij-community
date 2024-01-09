import junit.framework.TestCase;

public class AllConditionsTest extends TestCase {
  public void test() {
    AllConditions conditions = new AllConditions();

    conditions.test1(false, false);
    conditions.test2(false, false, false);
    conditions.test3(false, false, false, false);
  }

}