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
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/8/11
 * Time: 8:14 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ContentAnnotationCache {
  @Nullable
  ThreeState isRecent(VirtualFile vf, VcsKey vcsKey, VcsRevisionNumber number, TextRange range, long boundTime);

  void register(VirtualFile vf, VcsKey vcsKey, VcsRevisionNumber number, FileAnnotation fa);
}
