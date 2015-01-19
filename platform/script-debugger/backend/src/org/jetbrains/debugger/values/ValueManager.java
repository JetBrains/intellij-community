package org.jetbrains.debugger.values;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.ConsumerRunnable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.Vm;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The main idea of this class - don't create value for remote value handle if already exists. So,
 * implementation of this class keep map of value to remote value handle.
 * Also, this class maintains cache timestamp.
 *
 * Currently WIP implementation doesn't keep such map due to protocol issue. But V8 does.
 */
public abstract class ValueManager<VM extends Vm> {
  private final AtomicInteger cacheStamp = new AtomicInteger();
  private volatile boolean obsolete;

  protected final VM vm;

  protected ValueManager(VM vm) {
    this.vm = vm;
  }

  public void clearCaches() {
    cacheStamp.incrementAndGet();
  }

  @NotNull
  public ConsumerRunnable getClearCachesTask() {
    return new ConsumerRunnable() {
      @Override
      public void run() {
        clearCaches();
      }
    };
  }

  public final int getCacheStamp() {
    return cacheStamp.get();
  }

  public final boolean isObsolete() {
    return obsolete;
  }

  public final void markObsolete() {
    obsolete = true;
  }

  public final boolean rejectIfObsolete(@NotNull ActionCallback result) {
    if (isObsolete()) {
      result.reject("Obsolete context");
      return true;
    }
    return false;
  }

  @NotNull
  public static <T> Promise<T> reject() {
    return Promise.reject("Obsolete context");
  }

  @NotNull
  public VM getVm() {
    return vm;
  }
}