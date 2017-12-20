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

import com.intellij.openapi.vcs.VcsBundle;

/**
 * @author irengrig
 */
public enum ApplyPatchMode {
  APPLY(VcsBundle.message("patch.apply.dialog.title"), true),
  UNSHELVE(VcsBundle.message("unshelve.changes.dialog.title"), false),
  APPLY_PATCH_IN_MEMORY(VcsBundle.message("patch.apply.dialog.title"), false);

  private final String myTitle;
  private final boolean myCanChangePatchFile;

  ApplyPatchMode(String title, boolean canChangePatchFile) {
    myTitle = title;
    myCanChangePatchFile = canChangePatchFile;
  }

  public String getTitle() {
    return myTitle;
  }

  public boolean isCanChangePatchFile() {
    return myCanChangePatchFile;
  }
}
