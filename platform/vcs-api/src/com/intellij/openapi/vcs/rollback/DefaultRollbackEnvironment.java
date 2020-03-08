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

package com.intellij.openapi.vcs.rollback;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public abstract class DefaultRollbackEnvironment implements RollbackEnvironment {
  @Override
  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  public  String getRollbackOperationName() {
    return getRollbackOperationText();
  }

  @Override
  public void rollbackModifiedWithoutCheckout(final List<VirtualFile> files, final List<VcsException> exceptions,
                                              final RollbackProgressListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void rollbackIfUnchanged(final VirtualFile file) {
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  public static String getRollbackOperationText() {
    return VcsBundle.message("changes.action.rollback.text");
  }
}
