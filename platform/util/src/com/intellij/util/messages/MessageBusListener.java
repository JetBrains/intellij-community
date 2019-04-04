package com.intellij.util.messages;

import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public interface MessageBusListener<L> {
  @NotNull Topic<L> getTopic();
  @NotNull L getListener();
}
