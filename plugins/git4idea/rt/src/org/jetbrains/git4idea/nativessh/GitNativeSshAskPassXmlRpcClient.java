// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.git4idea.nativessh;

import java.net.URL;
import org.apache.xmlrpc.DefaultXmlRpcTransportFactory;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Vector;

/**
 * Calls {@link GitNativeSshAskPassXmlRpcClient} methods via XML RPC.
 */
class GitNativeSshAskPassXmlRpcClient {

  // Android Studio specific authentication to the builtin webserver
  @NotNull private final XmlRpcClient myClient;

  GitNativeSshAskPassXmlRpcClient(int port, String token) throws IOException {
    URL url = new URL("http", "127.0.0.1", port, "/RPC2");
    DefaultXmlRpcTransportFactory factory = new DefaultXmlRpcTransportFactory(url);
    factory.setBasicAuthentication("_token_", token);
    myClient = new XmlRpcClient(url, factory);
  }

  // Obsolete collection usage because of the XmlRpcClientLite API
  @SuppressWarnings({"UseOfObsoleteCollectionType", "unchecked"})
  String handleInput(String token, @NotNull String description) {
    Vector parameters = new Vector();
    parameters.add(token);
    parameters.add(description);

    try {
      return (String)myClient.execute(methodName("handleInput"), parameters);
    }
    catch (XmlRpcException | IOException e) {
      throw new RuntimeException("Invocation failed " + e.getMessage(), e);
    }
  }

  @NotNull
  private static String methodName(@NotNull String method) {
    return GitNativeSshAskPassXmlRpcHandler.HANDLER_NAME + "." + method;
  }
}
