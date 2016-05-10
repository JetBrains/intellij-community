/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.git4idea.http;

import org.apache.xmlrpc.XmlRpcClientLite;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Vector;

/**
 * Calls {@link GitAskPassXmlRpcHandler} methods via XML RPC.
 *
 * @author Kirill Likhodedov
 */
class GitAskPassXmlRpcClient {

  @NotNull private final XmlRpcClientLite myClient;

  GitAskPassXmlRpcClient(int port) throws MalformedURLException {
    myClient = new XmlRpcClientLite("127.0.0.1", port);
  }

  // Obsolete collection usage because of the XmlRpcClientLite API
  @SuppressWarnings({"UseOfObsoleteCollectionType", "unchecked"})
  String askUsername(String token, @NotNull String url) {
    Vector parameters = new Vector();
    parameters.add(token);
    parameters.add(url);

    try {
      return (String)myClient.execute(methodName("askUsername"), parameters);
    }
    catch (XmlRpcException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
    catch (IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  // Obsolete collection usage because of the XmlRpcClientLite API
  @SuppressWarnings({"UseOfObsoleteCollectionType", "unchecked"})
  String askPassword(String token, @NotNull String url) {
    Vector parameters = new Vector();
    parameters.add(token);
    parameters.add(url);

    try {
      return (String)myClient.execute(methodName("askPassword"), parameters);
    }
    catch (XmlRpcException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
    catch (IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  @NotNull
  private static String methodName(@NotNull String method) {
    return GitAskPassXmlRpcHandler.HANDLER_NAME + "." + method;
  }

}
