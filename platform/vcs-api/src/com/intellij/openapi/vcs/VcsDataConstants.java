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
package com.intellij.openapi.vcs;

/**
 * @deprecated use {@link com.intellij.openapi.vcs.VcsDataKeys} instead
 */
@SuppressWarnings({"UnusedDeclaration"})
public interface VcsDataConstants {
  String IO_FILE_ARRAY = VcsDataKeys.IO_FILE_ARRAY.getName();
  String IO_FILE = VcsDataKeys.IO_FILE.getName();
  String VCS_FILE_REVISION = VcsDataKeys.VCS_FILE_REVISION.getName();
  String VCS_FILE_REVISIONS = VcsDataKeys.VCS_FILE_REVISIONS.getName();
  String VCS_VIRTUAL_FILE = VcsDataKeys.VCS_VIRTUAL_FILE.getName();
  String FILE_PATH = VcsDataKeys.FILE_PATH.getName();
  String FILE_PATH_ARRAY = VcsDataKeys.FILE_PATH_ARRAY.getName();
  String FILE_HISTORY_PANEL = VcsDataKeys.FILE_HISTORY_PANEL.getName();
}
