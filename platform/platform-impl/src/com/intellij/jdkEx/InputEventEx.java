// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkEx;

import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

/**
 * Extends {@link InputEvent}.
 */
public interface InputEventEx {
  /**
   * JetBrains JDK: Returns the difference in nanoseconds between the timestamp
   * of when this event occurred and midnight, January 1, 1970 UTC.
   *
   * OpenJDK: Returns {@link InputEvent#getWhen()} result scaled to nanoseconds.
   *
   * @see InputEvent#getWhen()
   */
  long getWhenNano(@NotNull InputEvent inputEvent);
}

class DefInputEventEx implements InputEventEx {
  @Override
  public long getWhenNano(@NotNull InputEvent inputEvent) {
    return inputEvent.getWhen() * 1000000;
  }
}

class JBInputEventEx implements InputEventEx {
  @Override
  public long getWhenNano(@NotNull InputEvent inputEvent) {
    // todo: implement in JBRE
    return inputEvent.getWhen() * 1000000;
  }
}

