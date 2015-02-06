package org.jetbrains.concurrency;

import org.jetbrains.annotations.NotNull;

public interface AsyncFunction<PARAM, RESULT> {
  @NotNull
  Promise<RESULT> fun(PARAM param);
}