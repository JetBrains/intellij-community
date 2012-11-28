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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/20/12
 * Time: 3:09 PM
 */
public interface VcsAnnotationLocalChangesListener {
  // annotations for already committed revisions should not register with this method - they are not subject to refresh
  void registerAnnotation(VirtualFile file, FileAnnotation annotation);

  void unregisterAnnotation(VirtualFile file, FileAnnotation annotation);
}
