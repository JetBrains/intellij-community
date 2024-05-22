import java.nio.Buffer;
import java.nio.ByteBuffer;

public class TryToPreserveCast {
    public TryToPreserveCast() {
    }

    public static void main(String[] args) {

    }

    public void test(ByteBuffer buffer) {
        ((Buffer) buffer).limit(1);
        (buffer).limit(2);
    }
}