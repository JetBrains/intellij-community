package typeAnnotations;

import java.util.List;
import java.util.Map;

public class GenericArrayTypeAnnotations {
    @A List<Comparable<Object[][][]>> l1;
    List<@B Comparable<Object[][][]>> l2;
    List<Comparable<@F Object[][][]>> l3;
    List<Comparable<Object @C [][][]>> l4;
    List<Comparable<Object[] @D [][]>> l5;
    List<Comparable<Object[][] @E []>> l6;
    @A List<@B Comparable<@F Object @C [] @D [] @E []>> l7;
    @A List<@A Comparable<@A Object @A [] @A [] @A []>> l8;
    Map<Object @A @B [][], List<@E Map<Object @C [] @D [], @F Object>>> m1;
    @B @A Map<@A Object @F [] @E [], @D List<@F @A Map<@B @D Object @A @D [] @D [], @A @B @D Object>>> m2;
    @A @B Map<@B Map<@B Map<@B List<@F ? extends @A String @C [] @D [] @E @F []>, @A List<@B ? super @D String @E @A @F [] @F []>>, @A List<@C ?>>, @D List<@A Map<@E Object @F [] @D [], List<@D Object>> @A []> @B [] @C []> @D [] m3;

    @A Map<@A Object, @B List<@C Object @D [] @E [] @F []>> m12() {
        return null;
    }

    @A List<@B Object> @E [] @F [] l1() {
        return null;
    }

    @A List<@B Object @C [] @D []> @E [] @F [] l2() {
        return null;
    }

    @L List<Object[][]>[] @L [] l3() {
        return null;
    }
}
