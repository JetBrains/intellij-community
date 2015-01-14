package org.jetbrains.concurrency;

import com.intellij.util.Function;

public interface ObsolescentFunction<Param, Result> extends Function<Param, Result>, Obsolescent {
}