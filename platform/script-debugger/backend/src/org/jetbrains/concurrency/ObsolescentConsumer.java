package org.jetbrains.concurrency;

import com.intellij.util.Consumer;

public interface ObsolescentConsumer<T> extends Consumer<T>, Obsolescent {
}
