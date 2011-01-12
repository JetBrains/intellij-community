/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.commands;

/**
 * Implements GitTaskResultHandler doing nothing in each onEvent()-method.
 * Also introduces {@link #onFailure()} method which is called from all non successful event methods, except cancel.
 * One may override this method instead of each failure methods to handle them similarly.
 * @author Kirill Likhodedov
 */
public class GitTaskResultHandlerAdapter extends GitTaskResultHandler {
  @Override
  protected void onSuccess() {
  }

  @Override
  protected void onCancel() {
  }

  @Override
  protected void onException() {
    onFailure();
  }

  @Override
  protected void onGitError() {
    onFailure();
  }

  protected void onFailure() {
  }
}
