import kotlin.jvm.Transient;
import kotlin.jvm.Volatile;

public class Test {
    @Transient
    public int i;

    @Volatile
    public int j;
}
