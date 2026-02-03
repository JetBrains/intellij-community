// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jshell.protocol;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @author Eugene Zhuravlev
 */
public class MessageWriter<T extends Message> extends Endpoint {
  private final BufferedWriter myOut;

  public MessageWriter(OutputStream output) {
    myOut = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
  }

  public void send(T message) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(buffer)) { oos.writeObject(message); }
    String data = Base64.getEncoder().encodeToString(buffer.toByteArray());

    myOut.newLine();
    myOut.write(MSG_BEGIN);
    myOut.newLine();
    myOut.write(data);
    myOut.newLine();
    myOut.write(MSG_END);
    myOut.newLine();
    myOut.flush();
  }
}