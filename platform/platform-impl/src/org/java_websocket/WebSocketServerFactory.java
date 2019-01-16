/*
 *  Copyright (c) 2010-2018 Nathan Rajlich
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

package org.java_websocket;

import org.java_websocket.drafts.Draft;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * Interface to encapsulate the required methods for a websocket factory
 */
public interface WebSocketServerFactory extends WebSocketFactory {
    @Override
    WebSocketImpl createWebSocket(WebSocketAdapter a, Draft d);

    @Override
    WebSocketImpl createWebSocket(WebSocketAdapter a, List<Draft> drafts);

    /**
     * Allows to wrap the Socketchannel( key.channel() ) to insert a protocol layer( like ssl or proxy authentication) beyond the ws layer.
     *
     * @param channel The SocketChannel to wrap
     * @param key a SelectionKey of an open SocketChannel.
     * @return The channel on which the read and write operations will be performed.<br>
     * @throws IOException may be thrown while writing on the channel
     */
    ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException;

    /**
     * Allows to shutdown the websocket factory for a clean shutdown
     */
    void close();
}
