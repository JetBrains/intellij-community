// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.execution;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * Common server class that contains the boiler-plate code to set up a server socket.
 * The actual logic is delegated to the Protocol instance.
 */
public class SocketServer {
  protected ServerSocket myServerSocket;
  private final Protocol myProtocol;

  public SocketServer(Protocol protocol) {
    myProtocol = protocol;
  }

  public int start() throws IOException {
    myServerSocket = new ServerSocket(0);
    int port = myServerSocket.getLocalPort();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        boolean _continue = true;
        while (_continue) {
          Socket socket = myServerSocket.accept();
          try {
            _continue = myProtocol.handleConnection(socket);
          }
          finally {
            socket.close();
          }
        }
      }
      catch (SocketException e) {
        //socket was closed, that's OK
      }
      catch (IOException e) {
        throw new RuntimeException(e); //TODO implement catch clause
      }
    });

    return port;
  }

  public void stop() {
    try {
      if (myServerSocket != null) {
        myServerSocket.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e); //TODO implement catch clause
    }
  }

  public static abstract class Protocol {

    private static final int MAX_INPUT_LENGTH = 10 * 1000 * 1000;
    private static final Logger LOG = Logger.getInstance(Protocol.class);

    /**
     * Override this method to implement the actual logic of the protocol.
     *
     * @param socket The connected socket
     * @return {@code true} if the server should keep listening for new incoming requests,
     *         {@code false} if the server handling the protocol can be shutdown.
     * 
     * @throws IOException when the communication over the socket gives errors.
     */
    public abstract boolean handleConnection(Socket socket) throws IOException;

    protected static byte[] readDataBlock(DataInputStream inputStream) throws IOException {
      final int origLength = inputStream.readInt();
      final int length;
      if (origLength > MAX_INPUT_LENGTH) {
        length = MAX_INPUT_LENGTH;
        LOG.info(String.format("Too large input: %d bytes. Reading %s bytes and skipping all other.", origLength, length));
      } else {
        length = origLength;
      }
      byte[] data = new byte[length];

      readAsMuchAsAvailable(inputStream, data, length);

      int skipped = inputStream.skipBytes(origLength - length);
      if (skipped > 0) {
        LOG.info(String.format("Skipped %s bytes", skipped));
      }
      return data;
    }

    //if mercurial already sent number of bytes, but there was no data yet, this method read '\u0000' len times instead of data bytes.
    private static void readAsMuchAsAvailable(DataInputStream inputStream, byte[] data, int maxLength) throws IOException {
      int offset = 0;
      int available;
      while ((available = inputStream.available()) > 0) {
        if (available + offset > maxLength) {
          // read no more than maxLength
          inputStream.readFully(data, offset, maxLength - offset);
          return;
        }
        inputStream.readFully(data, offset, available);
        offset += available;
      }
    }

    protected static void sendDataBlock(DataOutputStream out, byte[] data) throws IOException {
      out.writeInt(data.length);
      out.write(data);
    }
  }

}
