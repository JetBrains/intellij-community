import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ThingTest {

    @Test
    public void testMakeThings() {
        doTest(new HashSet<>(Arrays.asList("a", "b")));
    }

    private void doTest(Set<String> expectedThings) {
        final Set<String> actualThings = new HashSet<>();
        for (Thing t : Thing.makeThings()) {
            actualThings.add(t.getName());
        }
        assertEquals(expectedThings, actualThings);
    }
}
class Thing {
    private String name;

    Thing(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    static Set<Thing> makeThings() {
        Set<Thing> things = new HashSet<>();
        things.add(new Thing("a"));
        things.add(new Thing("b"));
        return things;
    }
}