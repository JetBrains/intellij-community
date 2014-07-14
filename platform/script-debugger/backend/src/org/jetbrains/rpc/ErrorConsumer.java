package org.jetbrains.rpc;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.Nullable;

public interface ErrorConsumer<C extends ActionCallback, ERROR_DETAILS> {
  void consume(String errorMessage, @Nullable ERROR_DETAILS errorDetails, C callback);
}