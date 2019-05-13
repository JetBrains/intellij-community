import java.io.IOException;
import java.net.*;

public class AtParameter {
    void f() {
        try {
            throw new NoRouteToHostException();
        } catch (NoRouteToHostException | SocketException | IOException<caret> e) {
            e.printStackTrace();
        }
    }
}