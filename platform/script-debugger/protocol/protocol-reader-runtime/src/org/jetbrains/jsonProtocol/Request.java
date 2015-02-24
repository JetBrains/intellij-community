package org.jetbrains.jsonProtocol;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public interface Request<RESULT> {
  @NotNull
  ByteBuf getBuffer();

  String getMethodName();

  void finalize(int id);
}