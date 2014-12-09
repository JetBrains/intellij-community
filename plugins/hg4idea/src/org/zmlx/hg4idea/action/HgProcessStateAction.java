/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.action;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.zmlx.hg4idea.repo.HgRepository;

public abstract class HgProcessStateAction extends HgAbstractGlobalSingleRepoAction {
  final Repository.State myState;

  protected HgProcessStateAction(Repository.State state) {
    myState = state;
  }

  protected boolean isRebasing(AnActionEvent e) {
    HgRepository repository = HgActionUtil.getSelectedRepositoryFromEvent(e);
    return repository != null && repository.getState() == myState;
  }

  @Override
  public boolean isEnabled(AnActionEvent e) {
    return super.isEnabled(e) && isRebasing(e);
  }
}
