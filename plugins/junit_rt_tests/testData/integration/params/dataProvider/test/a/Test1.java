package a;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

@RunWith(DataProviderRunner.class)
public class Test1 {

  @DataProvider
  public static Object[][] dataProviderAdd() {
    // @formatter:off
    return new Object[][] {
      { 0 },
      { 1 },
      { 2 }
      /* ... */
    };
    // @formatter:on
  }

  @Test
  @UseDataProvider("dataProviderAdd")
  public void simple(int a) {
    System.out.println("Test" + a);
  }
}