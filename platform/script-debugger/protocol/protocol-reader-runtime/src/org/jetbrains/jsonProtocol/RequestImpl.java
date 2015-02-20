package org.jetbrains.jsonProtocol;

import java.io.IOException;

public abstract class RequestImpl<T> extends OutMessage implements Request<T> {
  protected boolean argumentsObjectStarted;

  protected abstract String getIdKeyName();

  protected abstract String argumentsKeyName();

  @Override
  protected final void beginArguments() throws IOException {
    if (!argumentsObjectStarted) {
      argumentsObjectStarted = true;
      writer.name(argumentsKeyName());
      writer.beginObject();
    }
  }

  @Override
  public final void finalize(int id) {
    try {
      if (argumentsObjectStarted) {
        writer.endObject();
      }
      writer.name(getIdKeyName()).value(id);
      writer.endObject();
      writer.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
