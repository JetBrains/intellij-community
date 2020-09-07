import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.List;

class MyTest {

  @NonNls List<String> test(@NonNls String input){
    @NonNls List<String> list = Arrays.asList("Text1", "Text2");
    return Arrays.asList("Text3", "Text4");
  }
}