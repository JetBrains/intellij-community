/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.data;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Collects incoming requests into a list, and provides them to the underlying background task via {@link #popRequests()}. <br/>
 * Such task is started immediately after the first request arrives, if no other task is currently running. <br/>
 * A task reports its completion by calling {@link #taskCompleted(Object)} and providing a result which is immediately passed to the
 * result handler (unless it is null in which case the task is stopped but the result is not passed to the handler).
 * <p/>
 * The purpose of this class is to provide a single thread, which processes incoming requests in the background and continues to process
 * new ones if they arrive while the previous ones were processed. An alternative would be a long living thread which always checks some
 * queue for new requests - but current approach starts a thread only when needed, and finishes it once all requests are processed.
 * <p/>
 * The class is thread-safe: all operations are synchronized.
 */
public abstract class SingleTaskController<Request, Result> {

  private static final Logger LOG = Logger.getInstance(SingleTaskController.class);

  @NotNull private final Consumer<Result> myResultHandler;
  @NotNull private final Object LOCK = new Object();

  @NotNull private List<Request> myAwaitingRequests;
  private boolean myActive;

  public SingleTaskController(@NotNull Consumer<Result> handler) {
    myResultHandler = handler;
    myAwaitingRequests = ContainerUtil.newArrayList();
  }

  /**
   * Posts a request into a queue. <br/>
   * If there is no active task, starts a new one. <br/>
   * Otherwise just remembers the request in the queue. Later it can be achieved by {@link #popRequests()}.
   */
  public final void request(@NotNull Request requests) {
    synchronized (LOCK) {
      myAwaitingRequests.add(requests);
      LOG.debug("Added requests: " + requests);
      if (!myActive) {
        startNewBackgroundTask();
        LOG.debug("Started a new bg task");
        myActive = true;
      }
    }
  }

  /**
   * Starts new task on a background thread. <br/>
   * <b>NB:</b> Don't invoke StateController methods inside this method, otherwise a deadlock will happen.
   */
  protected abstract void startNewBackgroundTask();

  /**
   * Returns all awaiting requests and clears the queue. <br/>
   * I.e. the second call to this method will return an empty list (unless new requests came via {@link #request(Object)}.
   */
  @NotNull
  protected final List<Request> popRequests() {
    synchronized (LOCK) {
      List<Request> requests = myAwaitingRequests;
      myAwaitingRequests = ContainerUtil.newArrayList();
      LOG.debug("Popped requests: " + requests);
      return requests;
    }
  }

  /**
   * The underlying currently active task should use this method to inform that it has completed the execution. <br/>
   * If the result is not null, it is immediately passed to the result handler specified in the constructor.
   * Otherwise result handler is not called, the task just completes.
   */
  protected final void taskCompleted(@Nullable Result result) {
    if (result != null) {
      myResultHandler.consume(result);
      LOG.debug("Handled result: " + result);
    }
    synchronized (LOCK) {
      if (myAwaitingRequests.isEmpty()) {
        myActive = false;
        LOG.debug("No more requests");
      }
      else {
        startNewBackgroundTask();
        LOG.debug("Restarted a bg task");
      }
    }
  }
}
