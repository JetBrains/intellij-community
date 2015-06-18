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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;

import java.util.Map;

/**
 * @author irengrig
 *         Date: 2/25/11
 *         Time: 5:18 PM
 */
public interface ApplyPatchExecutor<T extends AbstractFilePatchInProgress> {
  String getName();

  void apply(final MultiMap<VirtualFile, T> patchGroups,
             final LocalChangeList localList,
             String fileName,
             TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo);
}
