// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.git4idea.nativessh;

import org.apache.xmlrpc.XmlRpcClientLite;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Vector;

/**
 * Calls {@link GitNativeSshAskPassXmlRpcClient} methods via XML RPC.
 */
class GitNativeSshAskPassXmlRpcClient {

  @NotNull private final XmlRpcClientLite myClient;

  GitNativeSshAskPassXmlRpcClient(int port) throws MalformedURLException {
    myClient = new XmlRpcClientLite("127.0.0.1", port);
  }

  // Obsolete collection usage because of the XmlRpcClientLite API
  @SuppressWarnings({"UseOfObsoleteCollectionType", "unchecked"})
  String askPassphrase(String token, @NotNull String description) {
    Vector parameters = new Vector();
    parameters.add(token);
    parameters.add(description);

    try {
      return (String)myClient.execute(methodName("askPassphrase"), parameters);
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
