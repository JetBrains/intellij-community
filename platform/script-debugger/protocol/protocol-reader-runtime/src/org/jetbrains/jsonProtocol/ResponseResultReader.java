package org.jetbrains.jsonProtocol;

import org.jetbrains.io.JsonReaderEx;

public interface ResponseResultReader {
  Object readResult(String methodName, JsonReaderEx reader);
}