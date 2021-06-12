package a;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@RunWith(BlockJUnit4ClassRunner.class)
public class Test1 extends TestCase {

  private String myStr = null;

  @Before
  public void initData() {
    myStr = "myStr";
  }

  @Test
  public void simple() {
    Assert.assertTrue("myStr".equals(myStr));
  }
}
