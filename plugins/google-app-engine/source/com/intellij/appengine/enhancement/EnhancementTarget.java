/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.enhancement;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.openapi.compiler.generic.BuildTarget;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class EnhancementTarget extends BuildTarget {
  private AppEngineFacet myFacet;
  private final VirtualFile myOutputRoot;

  public EnhancementTarget(@NotNull AppEngineFacet facet, @NotNull VirtualFile outputRoot) {
    myFacet = facet;
    myOutputRoot = outputRoot;
  }

  @NotNull
  public AppEngineFacet getFacet() {
    return myFacet;
  }

  @NotNull
  public VirtualFile getOutputRoot() {
    return myOutputRoot;
  }

  @NotNull
  @Override
  public String getId() {
    return FacetPointersManager.constructId(myFacet);
  }
}
