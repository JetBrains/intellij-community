/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.pullrequests.overview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubConnection.PagedRequest;
import org.jetbrains.plugins.github.util.GithubAuthDataHolder;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GithubPagedRequestLoader<T> {
  @NotNull private final Project myProject;
  @NotNull private final GithubAuthDataHolder myAuthHolder;
  @NotNull private final Disposable myDisposable;
  @NotNull private final ResultConsumer<T> myHandler;

  @Nullable private Loader myLoader;

  public GithubPagedRequestLoader(@NotNull Project project,
                                  @NotNull GithubAuthDataHolder authHolder,
                                  @NotNull Disposable disposable,
                                  @NotNull ResultConsumer<T> handler) {
    myProject = project;
    myAuthHolder = authHolder;
    myDisposable = disposable;
    myHandler = handler;
  }

  @CalledInAwt
  public boolean loadRequest(@NotNull PagedRequest<T> request) {
    abort();

    myLoader = new Loader(request);
    return loadMore();
  }

  @CalledInAwt
  public boolean loadMore() {
    if (myLoader == null) return false;
    return myLoader.load();
  }

  @CalledInAwt
  public void abort() {
    if (myLoader != null) {
      myLoader.cancel();
      myLoader = null;
    }
  }

  private class Loader {
    @NotNull private final PagedRequest<T> myPagedRequest;

    @NotNull private final List<T> myResult = new ArrayList<>();
    @NotNull private final ProgressIndicator myIndicator = new EmptyProgressIndicator();

    private boolean myLoading = false;

    public Loader(@NotNull PagedRequest<T> pagedRequest) {
      myPagedRequest = pagedRequest;
    }

    @CalledInAwt
    public boolean load() {
      if (myLoading) return false;
      if (myIndicator.isCanceled()) return false;
      if (!myPagedRequest.hasNext()) return false;

      myLoading = true;

      BackgroundTaskUtil.executeOnPooledThread(myDisposable, () -> {
        try {
          List<T> page = GithubUtil.runTask(myProject, myAuthHolder, myIndicator, myPagedRequest::next);
          myResult.addAll(page);
        }
        catch (IOException e) {
          GithubNotifications.showError(myProject, "Request Failed", e);
        }

        List<T> finalResult = new ArrayList<T>(myResult);
        boolean finalHasNext = myPagedRequest.hasNext();

        ApplicationManager.getApplication().invokeLater(() -> {
          myLoading = false;
          if (myLoader != this) return;
          if (myIndicator.isCanceled()) return;

          myHandler.consume(finalResult, finalHasNext);
        });
      });

      return true;
    }

    public void cancel() {
      myIndicator.cancel();
    }
  }

  public interface ResultConsumer<T> {
    @CalledInAwt
    void consume(@NotNull List<T> result, boolean hasNext);
  }
}
