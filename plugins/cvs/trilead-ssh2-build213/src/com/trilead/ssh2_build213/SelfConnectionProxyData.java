package com.trilead.ssh2_build213;

import java.io.IOException;
import java.net.Socket;

public interface SelfConnectionProxyData extends ProxyData {
    Socket connect() throws IOException;
}
