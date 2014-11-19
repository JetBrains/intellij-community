package org.jetbrains.concurrency;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class Promise<T> {
  public static final Promise<Void> DONE = new DonePromise<Void>(null);
  public static final Promise<Void> REJECTED = new RejectedPromise<Void>(null);

  public enum State {
    PENDING, FULFILLED, REJECTED
  }

  public static <T> Promise<T> resolve(T result) {
    if (result == null) {
      //noinspection unchecked
      return (Promise<T>)DONE;
    }
    else {
      return new DonePromise<T>(result);
    }
  }

  @NotNull
  public static <T> Promise<T> reject(String result) {
    if (result == null) {
      //noinspection unchecked
      return (Promise<T>)REJECTED;
    }
    else {
      return new RejectedPromise<T>(result);
    }
  }

  public static Promise<Void> all(@NotNull Collection<Promise<?>> promises) {
    if (promises.isEmpty()) {
      return DONE;
    }

    final AsyncPromise<Void> totalPromise = new AsyncPromise<Void>();
    Consumer done = new CountDownConsumer(promises.size(), totalPromise);
    Consumer<String> rejected = new Consumer<String>() {
      @Override
      public void consume(String error) {
        if (totalPromise.state == AsyncPromise.State.PENDING) {
          totalPromise.setError(error);
        }
      }
    };

    for (Promise<?> promise : promises) {
      //noinspection unchecked
      promise.done(done);
      promise.rejected(rejected);
    }
    return totalPromise;
  }

  @NotNull
  public static Promise<Void> wrapAsVoid(@NotNull ActionCallback asyncResult) {
    final AsyncPromise<Void> promise = new AsyncPromise<Void>();
    asyncResult.doWhenDone(new Runnable() {
      @Override
      public void run() {
        promise.setResult(null);
      }
    }).doWhenRejected(new Consumer<String>() {
      @Override
      public void consume(String error) {
        promise.setError(error);
      }
    });
    return promise;
  }

  @NotNull
  public static <T> Promise<T> wrap(@NotNull AsyncResult<T> asyncResult) {
    final AsyncPromise<T> promise = new AsyncPromise<T>();
    asyncResult.doWhenDone(new Consumer<T>() {
      @Override
      public void consume(T result) {
        promise.setResult(result);
      }
    }).doWhenRejected(new Consumer<String>() {
      @Override
      public void consume(String error) {
        promise.setError(error);
      }
    });
    return promise;
  }

  @NotNull
  public abstract Promise<T> done(@NotNull Consumer<T> done);

  @NotNull
  public Promise<T> done(@NotNull ConsumerRunnable done) {
    //noinspection unchecked
    return done((Consumer<T>)done);
  }

  @NotNull
  public Promise<Void> then(@NotNull ConsumerRunnable done) {
    //noinspection unchecked
    return then((Function<T, Void>)done);
  }

  @NotNull
  public abstract Promise<T> rejected(@NotNull Consumer<String> rejected);

  public abstract void processed(@NotNull Consumer<T> processed);

  @NotNull
  public abstract <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<T, SUB_RESULT> done);

  //@NotNull
  //public abstract <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull PairConsumer<T, AsyncPromise<SUB_RESULT>> done);

  //public final <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull final SUB_RESULT result) {
  //  return then(new Function<T, SUB_RESULT>() {
  //    @Override
  //    public SUB_RESULT fun(T ignored) {
  //      return result;
  //    }
  //  });
  //}

  @NotNull
  public abstract <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull AsyncFunction<T, SUB_RESULT> done);

  @NotNull
  public abstract State getState();

  public final void notify(@NotNull final AsyncResult<T> result) {
    done(new Consumer<T>() {
      @Override
      public void consume(T t) {
        result.setDone(t);
      }
    });
    rejected(new Consumer<String>() {
      @Override
      public void consume(String error) {
        result.reject(error);
      }
    });
  }
}