/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.xmlrpc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A minimal web server that uses IDEA built-in pool
 *
 * @author Maxim.Mossienko
 */
public class IdeaAwareWebServer extends WebServer {
  private static final ExecutorService threadPool = Executors.newFixedThreadPool(2, new ThreadFactory() {
    public Thread newThread(final Runnable r) {
      return new Thread(r, "WebServer thread pool");
    }
  });

  /**
   * Creates a web server at the specified port number and IP
   * address.
   */
  public IdeaAwareWebServer(int port, InetAddress addr, XmlRpcServer xmlrpc) {
    super(port, addr, xmlrpc);
  }

  /**
   * @return
   */
  protected Runner getRunner() {
    return new MyRunner();
  }

  /**
   * Put <code>runner</code> back into {@link #threadpool}.
   *
   * @param runner The instance to reclaim.
   */
  void repoolRunner(Runner runner) {
  }

  /**
   * Responsible for handling client connections.
   */
  class MyRunner extends Runner {

    private Socket mySocket;

    /**
     * Handles the client connection on <code>socket</code>.
     *
     * @param socket The source to read the client's request from.
     */
    public synchronized void handle(Socket socket) throws IOException {
      mySocket = socket;
      con = new Connection(socket);
      count = 0;

      try {
        threadPool.submit(this);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }

    /**
     * Delegates to <code>con.run()</code>.
     */
    public void run() {
      try {
        con.run();
      }
      finally {
        Thread.interrupted(); // reset interrupted status
      }
    }

    public void shutdown() {
      try {
        mySocket.close();
      }
      catch (IOException e) {
        //pass
      }
    }
  }

  @Override
  public void shutdown() {
    super.shutdown();
    try {
      ServerSocket socket = serverSocket;
      if (socket != null) {
        socket.close();
      }
    }
    catch (IOException e) {
      //pass
    }
  }
}
