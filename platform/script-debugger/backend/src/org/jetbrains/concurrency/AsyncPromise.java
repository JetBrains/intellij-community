package org.jetbrains.concurrency;

import com.intellij.openapi.util.Getter;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AsyncPromise<T> extends Promise<T> implements Getter<T> {
  private volatile Consumer<T> done;
  private volatile Consumer<String> rejected;

  protected volatile State state = State.PENDING;
  // result object or error message
  private volatile Object result;

  @NotNull
  @Override
  public State getState() {
    return state;
  }

  @NotNull
  @Override
  public Promise<T> done(@NotNull Consumer<T> done) {
    switch (state) {
      case PENDING:
        break;
      case FULFILLED:
        //noinspection unchecked
        done.consume((T)result);
        return this;
      case REJECTED:
        return this;
    }

    this.done = setHandler(this.done, done);
    return this;
  }

  @NotNull
  @Override
  public Promise<T> rejected(@NotNull Consumer<String> rejected) {
    switch (state) {
      case PENDING:
        break;
      case FULFILLED:
        return this;
      case REJECTED:
        rejected.consume((String)result);
        return this;
    }

    this.rejected = setHandler(this.rejected, rejected);
    return this;
  }

  @Override
  public T get() {
    //noinspection unchecked
    return state == State.FULFILLED ? (T)result : null;
  }

  @SuppressWarnings("SynchronizeOnThis")
  private static final class CompoundConsumer<T> implements Consumer<T> {
    private List<Consumer<T>> consumers = new ArrayList<Consumer<T>>();

    public CompoundConsumer(@NotNull Consumer<T> c1, @NotNull Consumer<T> c2) {
      synchronized (this) {
        consumers.add(c1);
        consumers.add(c2);
      }
    }

    @Override
    public void consume(T t) {
      List<Consumer<T>> list;
      synchronized (this) {
        list = consumers;
        consumers = null;
      }

      if (list != null) {
        for (Consumer<T> consumer : list) {
          consumer.consume(t);
        }
      }
    }

    public void add(@NotNull Consumer<T> consumer) {
      synchronized (this) {
        if (consumers != null) {
          consumers.add(consumer);
        }
      }
    }
  }

  @Override
  @NotNull
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull final Function<T, SUB_RESULT> fulfilled) {
    switch (state) {
      case PENDING:
        break;
      case FULFILLED:
        //noinspection unchecked
        return new DonePromise<SUB_RESULT>(fulfilled.fun((T)result));
      case REJECTED:
        return new RejectedPromise<SUB_RESULT>((String)result);
    }

    assert done == null && rejected == null;
    final AsyncPromise<SUB_RESULT> promise = new AsyncPromise<SUB_RESULT>();
    addHandlers(new Consumer<T>() {
      @Override
      public void consume(T result) {
        try {
          if (fulfilled instanceof ObsolescentFunction && ((ObsolescentFunction)fulfilled).isObsolete()) {
            promise.setError("Obsolete");
          }
          else {
            promise.setResult(fulfilled.fun(result));
          }
        }
        catch (Throwable e) {
          promise.setError(e.getMessage());
        }
      }
    }, new Consumer<String>() {
      @Override
      public void consume(String error) {
        promise.setError(error);
      }
    });
    return promise;
  }

  void notify(@NotNull final AsyncPromise<T> child) {
    switch (state) {
      case PENDING:
        break;
      case FULFILLED:
        //noinspection unchecked
        child.setResult((T)result);
        return;
      case REJECTED:
        child.setError((String)result);
        return;
    }

    addHandlers(new Consumer<T>() {
      @Override
      public void consume(T result) {
        try {
          child.setResult(result);
        }
        catch (Throwable e) {
          child.setError(e.getMessage());
        }
      }
    }, new Consumer<String>() {
      @Override
      public void consume(String error) {
        child.setError(error);
      }
    });
  }

  @Override
  @NotNull
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull final AsyncFunction<T, SUB_RESULT> fulfilled) {
    switch (state) {
      case PENDING:
        break;
      case FULFILLED:
        //noinspection unchecked
        return fulfilled.fun((T)result);
      case REJECTED:
        return new RejectedPromise<SUB_RESULT>((String)result);
    }

    final AsyncPromise<SUB_RESULT> promise = new AsyncPromise<SUB_RESULT>();
    final Consumer<String> rejectedHandler = new Consumer<String>() {
      @Override
      public void consume(String error) {
        promise.setError(error);
      }
    };
    addHandlers(new Consumer<T>() {
      @Override
      public void consume(T result) {
        try {
          fulfilled.fun(result)
            .done(new Consumer<SUB_RESULT>() {
              @Override
              public void consume(SUB_RESULT result) {
                try {
                  promise.setResult(result);
                }
                catch (Throwable e) {
                  promise.setError(e.getMessage());
                }
              }
            })
            .rejected(rejectedHandler);
        }
        catch (Throwable e) {
          promise.setError(e.getMessage());
        }
      }
    }, rejectedHandler);
    return promise;
  }

  private void addHandlers(@NotNull Consumer<T> done, @NotNull Consumer<String> rejected) {
    this.done = setHandler(this.done, done);
    this.rejected = setHandler(this.rejected, rejected);
  }

  @NotNull
  private static <T> Consumer<T> setHandler(Consumer<T> oldConsumer, Consumer<T> newConsumer) {
    if (oldConsumer == null) {
      return newConsumer;
    }
    else if (oldConsumer instanceof CompoundConsumer) {
      ((CompoundConsumer<T>)oldConsumer).add(newConsumer);
      return oldConsumer;
    }
    else {
      return new CompoundConsumer<T>(oldConsumer, newConsumer);
    }
  }

  public void setResult(T result) {
    this.result = result;
    state = State.FULFILLED;

    Consumer<T> done = this.done;
    clearHandlers();
    if (done != null) {
      done.consume(result);
    }
  }

  public void setError(String error) {
    result = error;
    state = State.REJECTED;

    Consumer<String> rejected = this.rejected;
    clearHandlers();
    if (rejected != null) {
      rejected.consume(error);
    }
  }

  private void clearHandlers() {
    done = null;
    rejected = null;
  }

  @Override
  public void processed(@NotNull Consumer<T> processed) {
    assert done == null && rejected == null;
    done = processed;
    rejected = new Consumer<String>() {
      @Override
      public void consume(String error) {
        Consumer<T> rejected = done;
        if (rejected != null) {
          rejected.consume(null);
        }
      }
    };
  }
}