/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.DataKey;

public enum CommittedChangesBrowserUseCase {
  COMMITTED,
  INCOMING,
  UPDATE,
  IN_AIR;

  public static final DataKey<CommittedChangesBrowserUseCase> DATA_KEY = DataKey.create("COMMITTED_CHANGES_BROWSER_USE_CASE");
  @Deprecated public final static String CONTEXT_NAME = DATA_KEY.getName();
}
