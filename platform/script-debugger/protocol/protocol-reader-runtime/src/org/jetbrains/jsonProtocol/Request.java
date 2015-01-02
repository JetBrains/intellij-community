package org.jetbrains.jsonProtocol;

import org.jetbrains.annotations.NotNull;

public interface Request<RESULT> {
  @NotNull
  CharSequence toJson();

  @NotNull
  String getMethodName();

  void finalize(int id);
}