/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.zmlx.hg4idea;

import com.intellij.util.messages.Topic;

/**
 *
 */
public interface Topics {

  Topic<HgUpdater> BRANCH_TOPIC = new Topic<HgUpdater>("hg4idea.branch", HgUpdater.class);
  Topic<HgUpdater> REMOTE_TOPIC = new Topic<HgUpdater>("hg4idea.remote", HgUpdater.class);
  Topic<HgUpdater> STATUS_TOPIC = new Topic<HgUpdater>("hg4idea.status", HgUpdater.class);

} // End of Topics interface
