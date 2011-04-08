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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Future;

/**
 * Common server class that contains the boiler-plate code to set up a server socket.
 * The actual logic is delegated to the Protocol instance.
 */
public class SocketServer {
  protected ServerSocket myServerSocket;
  private final Protocol myProtocol;
  private Future<?> myExecutingFuture;

  public SocketServer(Protocol protocol) {
    myProtocol = protocol;
  }

  public int start() throws IOException {
    myServerSocket = new ServerSocket(0);
    int port = myServerSocket.getLocalPort();

    myExecutingFuture = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
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
      }
    });

    return port;
  }

  public void stop() {
    myExecutingFuture.cancel(true);
    try {
      myServerSocket.close();
    } catch (IOException e) {
      throw new RuntimeException(e); //TODO implement catch clause
    }
  }

  public static abstract class Protocol {

    /**
     * Override this method to implement the actual logic of the protocol.
     *
     * @param socket The connected socket
     * @return <code>true</code> if the server should keep listening for new incoming requests, 
     *         <code>false</code> if the server handling the protocol can be shutdown.
     * 
     * @throws IOException when the communication over the socket gives errors.
     */
    public abstract boolean handleConnection(Socket socket) throws IOException;

    protected byte[] readDataBlock(DataInputStream inputStream) throws IOException {
      int length = inputStream.readInt();
      byte[] data = new byte[length];
      inputStream.readFully(data);
      return data;
    }

    protected void sendDataBlock(DataOutputStream out, byte[] data) throws IOException {
      out.writeInt(data.length);
      out.write(data);
    }
  }

}
