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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.CalledInBackground;

import javax.swing.*;

/**
 * @author irengrig
 *         Date: 7/5/11
 *         Time: 2:49 PM
 */
public interface VcsChangeDetailsProvider<T> {
  String getProgressTitle();
  @CalledInAwt
  boolean canComment(final Change change);
  @CalledInBackground
  T load(final Change change);
  @CalledInAwt
  Pair<JPanel, Disposable> comment(final Change change, final T t);
}
