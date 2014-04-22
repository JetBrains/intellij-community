package org.jetbrains.rpc;

import org.jetbrains.annotations.NotNull;

public interface ResultReader<RESPONSE> {
  <RESULT> RESULT readResult(@NotNull String readMethodName, @NotNull RESPONSE successResponse);
}