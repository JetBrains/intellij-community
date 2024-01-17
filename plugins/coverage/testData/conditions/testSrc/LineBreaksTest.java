import junit.framework.TestCase;

public class LineBreaksTest extends TestCase {
  public void test() {
    LineBreaks l = new LineBreaks();

    l.doSwitch(3);
    l.doIf(1);
    l.ifVariable(true, true, true);
    l.ifMethods(true, true, true);
    l.andWithoutIfVariables(true, true, true);
    l.andWithoutIfMethods(true, true, true);
    l.forCycle(0);
    l.forEachCycle(new String[]{});
    l.whileCycle(0);
    l.doWhileCycle(0);
    l.ternaryOperator(true);
  }
}
