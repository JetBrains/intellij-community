import junit.framework.TestCase;

public class SwitchesTest extends TestCase {

  public void test() {
    Switches switches = new Switches();

    switches.singleBranchSwitch1(1);
    switches.singleBranchSwitch2(2);
    switches.defaultBranchSwitch(3);

    switches.fullyCoveredSwitch(1);
    switches.fullyCoveredSwitch(2);

    switches.fullyCoveredSwitchWithDefault(1);
    switches.fullyCoveredSwitchWithDefault(2);
    switches.fullyCoveredSwitchWithDefault(3);

    switches.fullyCoveredSwitchWithoutDefault(1);
    switches.fullyCoveredSwitchWithoutDefault(2);

    switches.fullyCoveredSwitchWithImplicitDefault(1);
    switches.fullyCoveredSwitchWithImplicitDefault(2);
    switches.fullyCoveredSwitchWithImplicitDefault(3);

    switches.switchWithFallThrough(1);

    switches.stringSwitch("A");
    switches.fullStringSwitch("A");
    switches.fullStringSwitch("B");
    switches.fullStringSwitch("C");

    switches.stringSwitchSameHashCode("Aa");
    switches.stringSwitchSameHashCode("BB");
    switches.stringSwitchSameHashCode("C");

  }

  public void testKotlin() {
    KtSwitches switches = new KtSwitches();

    switches.singleBranchSwitch1(1);
    switches.singleBranchSwitch2(2);
    switches.defaultBranchSwitch(3);

    switches.fullyCoveredSwitch(1);
    switches.fullyCoveredSwitch(2);

    switches.fullyCoveredSwitchWithDefault(1);
    switches.fullyCoveredSwitchWithDefault(2);
    switches.fullyCoveredSwitchWithDefault(3);

    switches.fullyCoveredSwitchWithoutDefault(1);
    switches.fullyCoveredSwitchWithoutDefault(2);

    switches.fullyCoveredSwitchWithImplicitDefault(1);
    switches.fullyCoveredSwitchWithImplicitDefault(2);
    switches.fullyCoveredSwitchWithImplicitDefault(3);
    switches.fullyCoveredSwitchWithImplicitDefault(4);

    switches.switchWithOnlyTwoBranchesTransformsIntoIf("A");

    switches.stringSwitch("A");
    switches.fullStringSwitch("A");
    switches.fullStringSwitch("B");
    switches.fullStringSwitch("C");
    switches.fullStringSwitch("D");

    switches.stringSwitchSameHashCode("Aa");
    switches.stringSwitchSameHashCode("BB");
    switches.stringSwitchSameHashCode("C");
    switches.stringSwitchSameHashCode("D");
    switches.stringSwitchSameHashCode("E");
  }
}
