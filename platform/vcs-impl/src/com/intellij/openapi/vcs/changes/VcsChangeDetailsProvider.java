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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;

import javax.swing.*;

/**
 * @author irengrig
 *         Date: 7/5/11
 *         Time: 2:49 PM
 */
public interface VcsChangeDetailsProvider {
  ExtensionPointName<VcsChangeDetailsProvider> EP_NAME = ExtensionPointName.create("com.intellij.vcschangedetails");

  String getName();

  @CalledInAwt
  boolean canComment(final Change change);
  @CalledInAwt
  RefreshablePanel comment(final Change change, JComponent parent, BackgroundTaskQueue queue);
}
