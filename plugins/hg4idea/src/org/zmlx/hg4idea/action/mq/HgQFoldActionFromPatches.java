/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea.action.mq;

import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.mq.HgQFoldCommand;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.List;

public class HgQFoldActionFromPatches extends HgActionFromMqPatches {

  @Override
  protected void execute(@NotNull HgRepository repository, @NotNull List<String> patchNames) {
    new HgQFoldCommand(repository).execute(patchNames);
  }

  @NotNull
  @Override
  protected String getTitle() {
    return "Folding patches...";
  }

  @Override
  protected boolean isEnabled(@NotNull HgRepository repository) {
    return !repository.getMQAppliedPatches().isEmpty();
  }
}
