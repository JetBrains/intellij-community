/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.util.containers.Convertor;
import com.intellij.util.continuation.*;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author irengrig
 */
public class LoadAlgorithm {
  private static final long ourTestTimeThreshold = 10000;

  private final Project myProject;
  private final List<LoaderAndRefresher<CommitHashPlusParents>> myLoaders;
  private final List<ByRootLoader> myShortLoaders;
  private Continuation myContinuation;
  private final GitCommitsSequentially myGitCommitsSequentially;

  public LoadAlgorithm(final Project project,
                       final List<LoaderAndRefresher<CommitHashPlusParents>> loaders,
                       final List<ByRootLoader> shortLoaders, final GitCommitsSequentially gitCommitsSequentially) {
    myProject = project;
    myLoaders = loaders;
    myShortLoaders = shortLoaders;
    myGitCommitsSequentially = gitCommitsSequentially;
  }

  public void setContinuation(Continuation continuation) {
    myContinuation = continuation;
  }

  public void fillContinuation() {
    final GatheringContinuationContext initContext = new GatheringContinuationContext();

    for (LoaderAndRefresher<CommitHashPlusParents> loader : myLoaders) {
      final LoaderFactory factory = new LoaderFactory(loader);
      final State state = new State(factory);
      state.scheduleSelf(initContext);
    }
    for (ByRootLoader shortLoader : myShortLoaders) {
      initContext.next(shortLoader);
    }
    /*for (ByRootLoader shortLoader : myShortLoaders) {
      myGitCommitsSequentially.pushUpdate(myProject, shortLoader.getRootHolder().getRoot(), initContext);
    }*/
    myContinuation.add(initContext.getList());
  }

  @CalledInAwt
  public void stop() {
    for (LoaderAndRefresher loader : myLoaders) {
      loader.interrupt();
    }
  }

  public Continuation getContinuation() {
    return myContinuation;
  }

  public void resume() {
    myContinuation.clearQueue();
    for (LoaderAndRefresher<CommitHashPlusParents> loader : myLoaders) {
      final LoaderFactory factory = new LoaderFactory(loader);
      myContinuation.add(Collections.<TaskDescriptor>singletonList(factory.convert(new State(factory))));
    }
    myContinuation.resumeOnNewIndicator(myProject, true, ProgressManager.getInstance().getProgressIndicator().getText());
  }

  private class LoadTaskDescriptor extends TaskDescriptor {
    protected final State myState;
    private final LoaderAndRefresher<CommitHashPlusParents> myLoader;

    protected LoadTaskDescriptor(final State state, final LoaderAndRefresher<CommitHashPlusParents> loader) {
      super("Load git tree skeleton", Where.POOLED);
      myState = state;
      myLoader = loader;
    }

    protected void processResult(final Result result) {
    }

    @Override
    public void run(final ContinuationContext context) {
      ProgressManager.progress(getName());
      final Result<CommitHashPlusParents> result = myLoader.load(myState.myValue, myState.getContinuationTs());
      processResult(result);
      myState.setContinuationTs(result.getLast() == null ? -1 : result.getLast().getTime());
      if (! result.isIsComplete()) {
        // no next stage if it is completed
        myState.transition();
        if (! myLoader.isInterrupted()) {
          myState.scheduleSelf(context);
        }
      }
      flushIntoUiCalledFromBackground(context, myLoader);
    }
  }

  private void flushIntoUiCalledFromBackground(final ContinuationContext context, LoaderAndRefresher<CommitHashPlusParents> uiRefresh) {
    final StepType stepType = uiRefresh.flushIntoUI();
    if (StepType.STOP.equals(stepType)) {
      context.cancelEverything();
    } else if (StepType.PAUSE.equals(stepType)) {
      context.cancelCurrent();
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.cancel();
      }
    }
  }

  private class TestLoadTaskDescriptor extends LoadTaskDescriptor {
    private TestLoadTaskDescriptor(final State state, final LoaderAndRefresher<CommitHashPlusParents> loader) {
      super(state, loader);
    }

    @Override
    protected void processResult(Result result) {
      assert LoadType.TEST.equals(myState.myValue);

      myState.takeDecision(false);
    }
  }

  public static enum LoadType {
    TEST(true, false),
    FULL_START(false, true),
    FULL(false, true),
    FULL_PREVIEW(true, true),
    SHORT_START(false, true),
    SHORT(false, true);

    private final boolean myStartEarly;
    private final boolean myUsesContinuation;

    private LoadType(final boolean startEarly, boolean startsContinuation) {
      myStartEarly = startEarly;
      myUsesContinuation = startsContinuation;
    }

    public boolean isStartEarly() {
      return myStartEarly;
    }

    public boolean isUsesContinuation() {
      return myUsesContinuation;
    }
  }

  public static class Result<T> {
    private final long myTime;
    private final boolean myIsComplete;
    private final T myLast;

    public Result(boolean isComplete, long time, final T t) {
      myIsComplete = isComplete;
      myTime = time;
      myLast = t;
    }

    public boolean isIsComplete() {
      return myIsComplete;
    }

    public long getTime() {
      return myTime;
    }

    public T getLast() {
      return myLast;
    }
  }

  private class LoaderFactory implements Convertor<State, LoadTaskDescriptor> {
    private final LoaderAndRefresher<CommitHashPlusParents> myLoader;

    private LoaderFactory(final LoaderAndRefresher<CommitHashPlusParents> loader) {
      myLoader = loader;
    }

    @Override
    public LoadTaskDescriptor convert(final State state) {
      if (LoadType.TEST.equals(state.myValue)) {
        return new TestLoadTaskDescriptor(state, myLoader);
      }
      return new LoadTaskDescriptor(state, myLoader);
    }
  }

  // pseudo enum
  private static class State {
    private boolean myLoadFull;
    private long myContinuationTs;
    @Nullable
    private LoadType myValue;
    private final Convertor<State, LoadTaskDescriptor> myLoaderFactory;

    private State(final Convertor<State, LoadTaskDescriptor> loaderFactory) {
      myContinuationTs = -1;
      myLoaderFactory = loaderFactory;
      myValue = LoadType.SHORT;
      myLoadFull = false;
    }

    public void scheduleSelf(final ContinuationContext context) {
      if (myValue == null) return;
      if (myValue.isStartEarly()) {
        context.next(myLoaderFactory.convert(this));
      } else {
        context.last(myLoaderFactory.convert(this));
      }
    }

    public long getContinuationTs() {
      return myContinuationTs;
    }

    public void setContinuationTs(long continuationTs) {
      myContinuationTs = continuationTs;
    }

    public void takeDecision(final boolean loadFull) {
      myLoadFull = loadFull;
    }

    public void transition() {
      if (LoadType.TEST.equals(myValue)) {
        myValue = myLoadFull ? LoadType.FULL_START : LoadType.FULL_PREVIEW;
      } else if (LoadType.FULL_PREVIEW.equals(myValue)) {
        myValue = LoadType.SHORT_START;
      } else if (LoadType.FULL_START.equals(myValue)) {
        myValue = LoadType.FULL;
      } else if (LoadType.SHORT_START.equals(myValue)) {
        myValue = LoadType.SHORT;
      } else if (LoadType.SHORT.equals(myValue)) {
        myValue = LoadType.SHORT;
      } else if (LoadType.FULL.equals(myValue)) {
        myValue = LoadType.FULL;
      } else {
        myValue = null;
      }
    }
  }
}
