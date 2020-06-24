// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.SLRUMap;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public final class RareLogger extends Logger {
  // key to last log time for key
  private final SLRUMap<Object, Long> myCache;
  private final List<LogFilter> myConverters = new LinkedList<>();
  private final LogFilter myProxy;
  private final Logger myLogger;

  private RareLogger(final Logger logger, final boolean fairSync) {
    myLogger = logger;

    final Object lock = new Object();
    myCache = new SLRUMap<Object, Long>(64, 32) {
      @Override
      public Long get(Object key) {
        if (fairSync) {
          synchronized (lock) {
            return super.get(key);
          }
        }
        return super.get(key);
      }

      @Override
      public void put(Object key, @NotNull Long value) {
        if (fairSync) {
          synchronized (lock) {
            super.put(key, value);
            return;
          }
        }
        super.put(key, value);
      }

      @Override
      public boolean remove(Object key) {
        if (fairSync) {
          synchronized (lock) {
            return super.remove(key);
          }
        }
        return super.remove(key);
      }
    };

    // just passes to parent logger
    myProxy = new LogFilter() {
      @Override
      @NotNull
      public Integer getAllowedLoggingInterval(Level level, String message, Throwable t, String[] details) {
        return -1;
      }
      @Override
      public Object getKey(@NotNull Level level,
                           @NonNls String message,
                           @Nullable Throwable t,
                           @NonNls String... details) {
        if (Level.DEBUG.equals(level)) {
          logger.debug(message, t);
        } else if (Level.INFO.equals(level)) {
          logger.info(message, t);
        } else if (Level.WARN.equals(level)) {
          logger.warn(message, t);
        } else if (Level.ERROR.equals(level)) {
          logger.error(message, t, details);
        }
        return null;
      }
    };
  }

  public void addFilter(final LogFilter logFilter) {
    myConverters.add(logFilter);
  }

  public static Logger wrap(final Logger logger, final boolean fairSync) {
    return new RareLogger(logger, fairSync);
  }

  public static Logger wrap(final Logger logger, final boolean fairSync, final LogFilter... filters) {
    final RareLogger rareLogger = new RareLogger(logger, fairSync);
    for (LogFilter filter : filters) {
      rareLogger.addFilter(filter);
    }
    return rareLogger;
  }

  @Override
  public void debug(@NonNls String message) {
    process(Level.DEBUG, message, null);
  }

  @Override
  public boolean isDebugEnabled() {
    return myLogger.isDebugEnabled();
  }

  @Override
  public void debug(Throwable t) {
    process(Level.DEBUG, null, t);
  }

  @Override
  public void debug(@NonNls String message, Throwable t) {
    process(Level.DEBUG, message, t);
  }

  @Override
  public boolean isTraceEnabled() {
    return myLogger.isTraceEnabled();
  }

  @Override
  public void trace(String message) {
    process(Level.TRACE, message, null);
  }

  @Override
  public void trace(@Nullable Throwable t) {
    process(Level.TRACE, null, t);
  }

  @Override
  public void error(@NonNls String message, @Nullable Throwable t, @NonNls String @NotNull ... details) {
    process(Level.ERROR, message, t, details);
  }

  @Override
  public void info(@NonNls String message) {
    process(Level.INFO, message, null);
  }

  @Override
  public void info(@NonNls String message, Throwable t) {
    process(Level.INFO, message, t);
  }

  @Override
  public void warn(@NonNls String message, Throwable t) {
    process(Level.WARN, message, t);
  }

  @Override
  public void setLevel(Level level) {
    myLogger.setLevel(level);
  }

  private void process(@NotNull final Level level, @NonNls @Nullable final String message, @Nullable final Throwable t, @NonNls final String... details) {
    if (! Level.ERROR.equals(level)) {
      for (LogFilter convertor : myConverters) {
        final Object key = convertor.getKey(level, message, t, details);
        if (key != null) {
          final Long latestMoment = myCache.get(key);
          if (latestMoment != null && ((System.currentTimeMillis() - latestMoment) < convertor.getAllowedLoggingInterval(level, message, t, details))) {
            return;
          } else {
            // log and put to cache
            myCache.put(key, System.currentTimeMillis());
            break;
          }
        }
      }
    }
    // pass
    myProxy.getKey(level, message, t, details);
  }

  public interface LogFilter {
    @Nullable
    Object getKey(@NotNull final Level level, @NonNls final String message, @Nullable final Throwable t, @NonNls final String... details);
    @NotNull
    Integer getAllowedLoggingInterval(Level level, String message, Throwable t, String[] details);
  }
}
