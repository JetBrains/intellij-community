/*
 * Copyright (c) 2010-2018 Nathan Rajlich
 *
 *  Permission is hereby granted, free of charge, to any person
 *  obtaining a copy of this software and associated documentation
 *  files (the "Software"), to deal in the Software without
 *  restriction, including without limitation the rights to use,
 *  copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following
 *  conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 *  OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 *  HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 *  OTHER DEALINGS IN THE SOFTWARE.
 */

package org.java_websocket.server;

import org.java_websocket.SSLSocketChannel2;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebSocketFactory that can be configured to only support specific protocols and cipher suites.
 */
public class CustomSSLWebSocketServerFactory extends DefaultSSLWebSocketServerFactory {

    /**
     * The enabled protocols saved as a String array
     */
    private final String[] enabledProtocols;

    /**
     * The enabled ciphersuites saved as a String array
     */
    private final String[] enabledCiphersuites;

    /**
     * New CustomSSLWebSocketServerFactory configured to only support given protocols and given cipher suites.
     *
     * @param sslContext          - can not be <code>null</code>
     * @param enabledProtocols    - only these protocols are enabled, when <code>null</code> default settings will be used.
     * @param enabledCiphersuites - only these cipher suites are enabled, when <code>null</code> default settings will be used.
     */
    public CustomSSLWebSocketServerFactory(SSLContext sslContext, String[] enabledProtocols, String[] enabledCiphersuites) {
        this(sslContext, Executors.newSingleThreadScheduledExecutor(), enabledProtocols, enabledCiphersuites);
    }

    /**
     * New CustomSSLWebSocketServerFactory configured to only support given protocols and given cipher suites.
     *
     * @param sslContext          - can not be <code>null</code>
     * @param executerService     - can not be <code>null</code>
     * @param enabledProtocols    - only these protocols are enabled, when <code>null</code> default settings will be used.
     * @param enabledCiphersuites - only these cipher suites are enabled, when <code>null</code> default settings will be used.
     */
    public CustomSSLWebSocketServerFactory(SSLContext sslContext, ExecutorService executerService, String[] enabledProtocols, String[] enabledCiphersuites) {
        super(sslContext, executerService);
        this.enabledProtocols = enabledProtocols;
        this.enabledCiphersuites = enabledCiphersuites;
    }

    @Override
    public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException {
        SSLEngine e = sslcontext.createSSLEngine();
        if (enabledProtocols != null) {
            e.setEnabledProtocols(enabledProtocols);
        }
        if (enabledCiphersuites != null) {
            e.setEnabledCipherSuites(enabledCiphersuites);
        }
        e.setUseClientMode(false);
        return new SSLSocketChannel2(channel, e, exec, key);
    }

}