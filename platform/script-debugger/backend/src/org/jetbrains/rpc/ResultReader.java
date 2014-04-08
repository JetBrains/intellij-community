package org.jetbrains.rpc;

public interface ResultReader<RESPONSE> {
  <RESULT> RESULT readResult(String readMethodName, RESPONSE successResponse);
}