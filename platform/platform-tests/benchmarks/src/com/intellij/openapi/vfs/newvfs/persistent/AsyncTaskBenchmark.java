// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorsKt;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

import static com.intellij.openapi.vfs.newvfs.persistent.AsyncKt.*;
import static com.intellij.util.io.BlockingKt.getBlockingDispatcher;
import static java.util.concurrent.TimeUnit.*;

/**
 * Benchmark async task overhead on old-school thread pools vs coroutines
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = SECONDS)
@Measurement(iterations = 4, time = 10, timeUnit = SECONDS)
@Threads(1)
@Fork(1)
public class AsyncTaskBenchmark {


  @State(Scope.Benchmark)
  public static class BaseContext {

    @Param("5000")
    public long baselineTaskDurationNs;

    public Callable<Long> taskToRunAsync;

    @Setup
    public void setup() {
      taskToRunAsync = new SlowTask(baselineTaskDurationNs);
    }
  }

  @State(Scope.Benchmark)
  public static class AsyncContext {

    private ExecutorService executorService;
    @Param({
      "thread-pool",
      "thread-pool-queried",

      "coroutine-deferred-blocking-dispatcher",
      "coroutine-deferred-io-dispatcher",
      "coroutine-deferred-default-dispatcher",
      "coroutine-deferred-via-thread-pool-dispatcher",

      "coroutine-direct-dispatch-blocking-dispatcher",
      "coroutine-direct-dispatch-io-dispatcher",
      "coroutine-direct-dispatch-default-dispatcher",
      "coroutine-direct-dispatch-via-thread-pool-dispatcher"
    })
    public String METHOD;

    public Callable<Long> asyncWrapper;


    @Setup
    public void setup(BaseContext dataContext) {
      var taskToRunAsync = dataContext.taskToRunAsync;
      switch (METHOD) {
        case "thread-pool": {
          //copied from ProcessIOExecutorService:
          executorService = new ThreadPoolExecutor(
            /* poolSize: */  1, Integer.MAX_VALUE,
            /* keepAlive: */ 1, MINUTES,
                             new SynchronousQueue<>() //rendezvous queue
          );
          asyncWrapper = () -> executorService.submit(taskToRunAsync).get();
          break;
        }

        case "thread-pool-queried": {
          executorService = new ThreadPoolExecutor(
            /* poolSize: */  1, Integer.MAX_VALUE,
            /* keepAlive: */ 1, MINUTES,
                             new ArrayBlockingQueue<>(64) //use actual queue instead of rendezvous
          );
          asyncWrapper = () -> executorService.submit(taskToRunAsync).get();
          break;
        }


        case "coroutine-deferred-blocking-dispatcher": {
          asyncWrapper = () -> runTaskAsyncViaDeferred(taskToRunAsync, getBlockingDispatcher());
          break;
        }

        case "coroutine-deferred-default-dispatcher": {
          asyncWrapper = () -> runTaskAsyncViaDeferred(taskToRunAsync, Dispatchers.getDefault());
          break;
        }

        case "coroutine-deferred-io-dispatcher": {
          asyncWrapper = () -> runTaskAsyncViaDeferred(taskToRunAsync, Dispatchers.getIO());
          break;
        }


        case "coroutine-direct-dispatch-blocking-dispatcher": {
          asyncWrapper = () -> runTaskAsyncViaDirectDispatch(taskToRunAsync, getBlockingDispatcher());
          break;
        }

        case "coroutine-direct-dispatch-default-dispatcher": {
          asyncWrapper = () -> runTaskAsyncViaDirectDispatch(taskToRunAsync, Dispatchers.getDefault());
          break;
        }

        case "coroutine-direct-dispatch-io-dispatcher": {
          asyncWrapper = () -> runTaskAsyncViaDirectDispatch(taskToRunAsync, Dispatchers.getIO());
          break;
        }


        case "coroutine-deferred-via-thread-pool-dispatcher": {
          executorService = new ThreadPoolExecutor(
            /* poolSize: */  1, Integer.MAX_VALUE,
            /* keepAlive: */ 1, MINUTES,
                             new ArrayBlockingQueue<>(64)
          );
          CoroutineDispatcher dispatcher = ExecutorsKt.from(executorService);
          asyncWrapper = () -> {
            return runTaskAsyncViaDeferred(taskToRunAsync, dispatcher);
          };
          break;
        }

        case "coroutine-direct-dispatch-via-thread-pool-dispatcher": {
          executorService = new ThreadPoolExecutor(
            /* poolSize: */  1, Integer.MAX_VALUE,
            /* keepAlive: */ 1, MINUTES,
                             new ArrayBlockingQueue<>(64)
          );
          CoroutineDispatcher dispatcher = ExecutorsKt.from(executorService);
          asyncWrapper = () -> runTaskAsyncViaDirectDispatch(taskToRunAsync, dispatcher);
          break;
        }

        default:
          throw new IllegalStateException("METHOD: " + METHOD + " is unrecognized");
      }
    }

    @TearDown
    public void tearDown() throws InterruptedException {
      if (executorService != null) {
        executorService.shutdown();
        executorService.awaitTermination(1, SECONDS);
      }
    }
  }

  @Benchmark
  public Long directCall(BaseContext context) throws Exception {
    return context.taskToRunAsync.call();
  }

  @Benchmark
  public Long asyncCall(BaseContext context,
                        AsyncContext asyncContext) throws Exception {
    return asyncContext.asyncWrapper.call();
  }


  public static class SlowTask implements Callable<Long> {

    private final long delayNs;

    public SlowTask(long delayNs) { this.delayNs = delayNs; }

    @Override
    public Long call() throws Exception {
      long startedAt = System.nanoTime();
      LockSupport.parkNanos(delayNs);
      return System.nanoTime() - startedAt;
    }
  }

  public static void main(final String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs()
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(10_000)
      //.warmupTime(seconds(10))
      //.warmupBatchSize(1000)
      //.measurementIterations(10_000)
      .include(AsyncTaskBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .forks(1)
      .build();

    new Runner(opt).run();
  }
}
