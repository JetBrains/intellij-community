// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.util.progress.CancellationUtil;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark different locks acquisition with different 'cancellation' methods
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 1, time = 5, timeUnit = SECONDS)
@Threads(1)
@Fork(1)
public class CancellableLockBenchmark {

  public static final int TASK_SIZE_UNDER_LOCK = 80;
  /** Does nothing */
  private static final Lock DUMMY_LOCK = new Lock() {
    @Override
    public void lock() {
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
    }

    @Override
    public boolean tryLock() {
      return true;
    }

    @Override
    public boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException {
      return true;
    }

    @Override
    public void unlock() {
    }

    @NotNull
    @Override
    public Condition newCondition() {
      throw new UnsupportedOperationException("Method is not implemented");
    }
  };

  @State(Scope.Benchmark)
  public static class LockContext {

    @Param({"dummyLock", "reentrantLock", "readWrite.readLock", "readWrite.writeLock"})
    public String LOCK_KIND;

    public Lock lockToAcquire;

    private final ReentrantLock reentrantLock = new ReentrantLock();
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    @Setup
    public void setup() {
      lockToAcquire = switch (LOCK_KIND) {
        case "dummyLock" -> DUMMY_LOCK;
        case "reentrantLock" -> reentrantLock;
        case "readWrite.readLock" -> readWriteLock.readLock();
        case "readWrite.writeLock" -> readWriteLock.writeLock();
        default -> throw new IllegalStateException("LOCK_KIND: " + LOCK_KIND + " is unrecognized");
      };
    }
  }

  @Benchmark
  public void noLock_baseline(LockContext context) {
    Blackhole.consumeCPU(TASK_SIZE_UNDER_LOCK);
  }

  @Benchmark
  public void lock_Raw(LockContext context) throws Exception {
    final Lock lock = context.lockToAcquire;
    lock.lock();
    try {
      Blackhole.consumeCPU(TASK_SIZE_UNDER_LOCK);
    }
    finally {
      lock.unlock();
    }
  }

  @Benchmark
  public void lock_WithCheckCancelled(ApplicationContext applicationContext,
                                      LockContext context) throws Exception {
    //RC: need applicationContext (i.e. application it contains) to initialize
    //    ProgressManager instance
    final Lock lock = context.lockToAcquire;
    ProgressIndicatorUtils.awaitWithCheckCanceled(lock);
    try {
      Blackhole.consumeCPU(TASK_SIZE_UNDER_LOCK);
    }
    finally {
      lock.unlock();
    }
  }

  @Benchmark
  public void lock_WithMaybeCancellable(LockContext context) throws Exception {
    final Lock lock = context.lockToAcquire;
    CancellationUtil.lockMaybeCancellable(lock);
    try {
      Blackhole.consumeCPU(TASK_SIZE_UNDER_LOCK);
    }
    finally {
      lock.unlock();
    }
  }


  public static void main(final String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
               "--add-opens=java.base/java.util=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED"

               //to enable 'new' API:
               //"-Dvfs.lock-free-impl.enable=true",
               //"-Dvfs.lock-free-impl.fraction-direct-memory-to-utilize=0.5",
               //"-Dvfs.use-streamlined-attributes-storage=true"
      )
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include(CancellableLockBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .build();

    new Runner(opt).run();
  }
}
