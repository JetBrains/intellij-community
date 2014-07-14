/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

public enum LoadMoreStage {
  /**
   * Initial table view; "load more" was not requested yet.
   */
  INITIAL,

  /**
   * "Load more" was once requested with a limited number of commits.
   */
  LOADED_MORE,

  /**
   * All commits matching the given filters were requested from the VCS, requesting more won't cause any effect.
   */
  ALL_REQUESTED
}
