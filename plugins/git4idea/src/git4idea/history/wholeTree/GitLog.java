/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vcs.CalledInAny;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.util.List;

/**
 * @author irengrig
 */
public interface GitLog extends Disposable {
  @CalledInAny
  void rootsChanged(final List<VirtualFile> roots);
  @CalledInAwt
  JComponent getVisualComponent();
  void setModalityState(ModalityState state);

  /** Find and select commit in git log UI */
  void selectCommit(String commitId);
}
