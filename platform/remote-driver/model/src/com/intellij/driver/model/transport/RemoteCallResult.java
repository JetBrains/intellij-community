package com.intellij.driver.model.transport;

import java.io.Serial;
import java.io.Serializable;

public record RemoteCallResult(
  Object value
) implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
}
