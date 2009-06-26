/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.IndexedRootsProvider;
import gnu.trove.THashSet;
import standardDsls.Marker;

import java.util.Set;

/**
 * @author peter
 */
public class StandardDslIndexedRootsProvider implements IndexedRootsProvider {
  private final Set<String> ourDsls = new THashSet<String>();

  public StandardDslIndexedRootsProvider() {
    final VirtualFile parent = VfsUtil.findFileByURL(Marker.class.getResource("Marker.class")).getParent();
    //RefreshQueue.getInstance().refresh(true, true, null, parent);
    if (parent != null) {
      for (VirtualFile file : parent.getChildren()) {
        if ("gdsl".equals(file.getExtension())) {
          ourDsls.add(file.getUrl());
        }
      }
    }
  }

  public Set<String> getRootsToIndex() {
    return ourDsls;
  }
}
