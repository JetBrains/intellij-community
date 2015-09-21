/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.util.Consumer;

/**
 * TODO: To be removed in IDEA 16.
 * @author irengrig
 *         Date: 12/15/10
 *         Time: 5:42 PM
 */
@Deprecated
public interface VcsEventsListenerManager {
  void removeCheckin(final Object key);
  void removeUpdate(final Object key);
  void removeRollback(final Object key);

  Object addCheckin(Consumer<Pair<VcsKey, Consumer<CheckinEnvironment>>> consumer);
  Object addUpdate(Consumer<Pair<VcsKey, Consumer<UpdateEnvironment>>> consumer);
  Object addRollback(Consumer<Pair<VcsKey, Consumer<RollbackEnvironment>>> consumer);
}
