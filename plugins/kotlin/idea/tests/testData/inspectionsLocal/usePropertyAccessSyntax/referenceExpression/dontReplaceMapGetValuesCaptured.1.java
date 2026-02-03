import java.util.AbstractMap;
import java.util.List;

public abstract class Test extends AbstractMap<String, Object> {
    private List<String> values;

    public String getValues() {
        return "hello";
    }
}
