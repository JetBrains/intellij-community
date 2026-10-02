// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import org.jetbrains.annotations.NotNull;


final class CommandIdImpl implements CommandId {
  private final long id;

  CommandIdImpl(long id) {
    this.id = id;
  }

  @Override
  public boolean isCompatible(@NotNull CommandId commandId) {
    if (commandId instanceof CommandIdImpl) {
      CommandIdImpl impl = (CommandIdImpl) commandId;
      return (id > 0 && impl.id > 0 || id < 0 && impl.id < 0);
    }
    return false;
  }

  @Override
  public long asLong() {
    return id;
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof CommandIdImpl)) {
      return false;
    }
    return id == ((CommandIdImpl) object).id;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(id);
  }

  @Override
  public String toString() {
    return String.valueOf(id);
  }
}
