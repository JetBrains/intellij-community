import junit.framework.TestCase;

public class ConditionsTest extends TestCase {
  public void test() {
    Conditions conditions = new Conditions();

    conditions.oneBranch1(1);
    conditions.oneBranch2(2);

    conditions.allBranches(1);
    conditions.allBranches(2);

    conditions.singleBranch1(1);
    conditions.singleBranch2(2);

    // is not called on purpose
    // conditions.empty(1);

    conditions.and1(true, false);
    conditions.and2(false, true);
    conditions.and3(true, true);

    conditions.fullAnd(true, true);
    conditions.fullAnd(true, false);
    conditions.fullAnd(false, true);
    conditions.fullAnd(false, false);

    conditions.andAnd0(false, false, false);

    conditions.andAnd1(false, false, false);
    conditions.andAnd1(true, false, false);

    conditions.andAnd2(false, false, false);
    conditions.andAnd2(true, false, false);
    conditions.andAnd2(true, true, false);

    conditions.andAnd3(false, false, false);
    conditions.andAnd3(true, false, false);
    conditions.andAnd3(true, true, false);
    conditions.andAnd3(true, true, true);

    conditions.or1(true, false);
    conditions.or2(false, true);
    conditions.or3(true, true);

    conditions.fullOr(true, true);
    conditions.fullOr(true, false);
    conditions.fullOr(false, true);
    conditions.fullOr(false, false);

    conditions.negation(true);
    conditions.manualNegation(true);

    conditions.andWithoutIf(true, false);
    conditions.orWithoutIf(false, true);

    conditions.forCycle(3);
    conditions.forEachCycle();
    conditions.whileCycle(3);

    conditions.ternaryOperator1(true);
    conditions.ternaryOperator2(false);
    conditions.ternaryOperatorFull(true);
    conditions.ternaryOperatorFull(false);

    conditions.ternaryOr(false, true);
  }

  public void testKotlin() {
    KtConditions conditions = new KtConditions();

    conditions.oneBranch1(1);
    conditions.oneBranch2(2);

    conditions.allBranches(1);
    conditions.allBranches(2);

    conditions.singleBranch1(1);
    conditions.singleBranch2(2);

    // is not called on purpose
    // conditions.empty(1);

    conditions.and1(true, false);
    conditions.and2(false, true);
    conditions.and3(true, true);

    conditions.fullAnd(true, true);
    conditions.fullAnd(true, false);
    conditions.fullAnd(false, true);
    conditions.fullAnd(false, false);

    conditions.andAnd0(false, false, false);

    conditions.andAnd1(false, false, false);
    conditions.andAnd1(true, false, false);

    conditions.andAnd2(false, false, false);
    conditions.andAnd2(true, false, false);
    conditions.andAnd2(true, true, false);

    conditions.andAnd3(false, false, false);
    conditions.andAnd3(true, false, false);
    conditions.andAnd3(true, true, false);
    conditions.andAnd3(true, true, true);

    conditions.or1(true, false);
    conditions.or2(false, true);
    conditions.or3(true, true);

    conditions.fullOr(true, true);
    conditions.fullOr(true, false);
    conditions.fullOr(false, true);
    conditions.fullOr(false, false);

    conditions.negation(true);
    conditions.manualNegation(true);

    conditions.andWithoutIf(true, false);
    conditions.orWithoutIf(false, true);

    conditions.forCycle(3);
    conditions.forEachCycle();
    conditions.whileCycle(3);

    conditions.ternaryOperator1(true);
    conditions.ternaryOperator2(false);
    conditions.ternaryOperatorFull(true);
    conditions.ternaryOperatorFull(false);

    conditions.ternaryOr(false, true);
  }
}
