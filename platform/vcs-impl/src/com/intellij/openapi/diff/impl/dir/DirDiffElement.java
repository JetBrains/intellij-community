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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffElement {
  private final ElementType myType;
  private final VirtualFile mySource;
  private final long mySourceLength;
  private final VirtualFile myTarget;
  private final long myTargetLength;
  private final String myName;

  private DirDiffElement(@Nullable VirtualFile source, @Nullable VirtualFile target, ElementType type, String name) {
    myType = type;
    mySource = source;
    mySourceLength = source == null || source.isDirectory() ? -1 : source.getLength();
    myTarget = target;
    myTargetLength = target == null || target.isDirectory() ? -1 : target.getLength();
    myName = name;
  }

  public static DirDiffElement createChange(@NotNull VirtualFile source, @NotNull VirtualFile target) {
    return new DirDiffElement(source, target, ElementType.CHANGED, source.getName());
  }

  public static DirDiffElement createSourceOnly(@NotNull VirtualFile source) {
    return new DirDiffElement(source, null, ElementType.SOURCE, null);
  }

  public static DirDiffElement createTargetOnly(@NotNull VirtualFile target) {
    return new DirDiffElement(null, target, ElementType.TARGET, null);
  }

  public static DirDiffElement createDirElement(VirtualFile src, VirtualFile trg, String name) {
    return new DirDiffElement(src, trg, ElementType.SEPARATOR, name);
  }

  public ElementType getType() {
    return myType;
  }

  public VirtualFile getSource() {
    return mySource;
  }

  public VirtualFile getTarget() {
    return myTarget;
  }

  public String getName() {
    return myName;
  }

  @Nullable
  public String getSourceName() {
    return myType == ElementType.CHANGED || myType == ElementType.SOURCE
           ? mySource.getName() : null;
  }

  @Nullable
  public String getSourceSize() {
    return mySourceLength < 0 ? null : String.valueOf(mySourceLength);
  }

  @Nullable
  public String getTargetName() {
    return myType == ElementType.CHANGED || myType == ElementType.TARGET
           ? myTarget.getName() : null;
  }

  @Nullable
  public String getTargetSize() {
    return myTargetLength < 0 ? null : String.valueOf(myTargetLength);
  }

  public boolean isSeparator() {
    return myType == ElementType.SEPARATOR;
  }

  public static enum ElementType {SOURCE, TARGET, SEPARATOR, CHANGED}
}
