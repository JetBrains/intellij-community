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
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConstantFunction;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class EmptyDataPack {

  @NotNull
  public static DataPack getInstance() {
    RefsModel emptyModel = new RefsModel(Collections.<VirtualFile, Collection<VcsRef>>emptyMap(), new ConstantFunction<Hash, Integer>(0));
    return new DataPack(emptyModel, EmptyPermanentGraph.getInstance(), new EmptyGraphFacade(), Collections.<VirtualFile, VcsLogProvider>emptyMap(), false);
  }

}
