package org.jetbrains.jsonProtocol;

public interface Request {
  CharSequence toJson();

  String getMethodName();

  void finalize(int id);
}
