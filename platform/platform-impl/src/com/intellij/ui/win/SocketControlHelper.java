/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui.win;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * @author Denis Fokin
 */
public class SocketControlHelper {

  private static final String ACTIVATE_COMMAND = "activate ";

  /*
   * args[0] - socket of controlled IDE
   * args[1] - project path
   */
  public static void main(String[] args) {
    final int portNumber = Integer.parseInt(args[0]);
    final String pathToProject = new File(args[1]).getAbsolutePath();
    final InetAddress lba = getLoopbackAddress();

    try {
      Socket socket = new Socket(lba, portNumber);
      socket.setSoTimeout(300);
      DataInputStream in = new DataInputStream(socket.getInputStream());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      try {

        out.writeUTF(ACTIVATE_COMMAND + new File(".").getAbsolutePath() + "\0" + "reopen" + "\0" + pathToProject);
        out.flush();
        String response = in.readUTF();
        if (response.equals("ok")) {
          System.err.println("Activated.");
        }
      } finally {
        in.close();
        out.close();
        socket.close();
      }
    } catch (IOException ignored) {}

    System.err.println("Activation failed");
  }

  private static InetAddress getLoopbackAddress() {
    InetAddress returnValue = null;
    try {
      returnValue = InetAddress.getByName("127.0.0.1");
    } catch (UnknownHostException uhe) {
      uhe.printStackTrace();
    }
    return returnValue;
  }
}
