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

import com.intellij.ide.diff.DiffElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import static com.intellij.openapi.diff.impl.dir.DirDiffOperation.*;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffElement {
  private final ElementType myType;
  private final DiffElement mySource;
  private final long mySourceLength;
  private final DiffElement myTarget;
  private final long myTargetLength;
  private final String myName;
  private DirDiffOperation myOperation;

  private DirDiffElement(@Nullable DiffElement source, @Nullable DiffElement target, ElementType type, String name) {
    myType = type;
    mySource = source;
    mySourceLength = source == null || source.isContainer() ? -1 : source.getSize();
    myTarget = target;
    myTargetLength = target == null || target.isContainer() ? -1 : target.getSize();
    myName = name;
    if (isSource()) {
      myOperation = COPY_TO;
    }
    else if (isTarget()) {
      myOperation = DirDiffOperation.COPY_FROM;
    }
    else if (type == ElementType.CHANGED) {
      assert source != null;
      myOperation = source.getFileType().isBinary() ? NONE : DirDiffOperation.MERGE;
    }
  }

  public String getSourceModificationDate() {
    return mySource == null ? "" : getLastModification(mySource);
  }

  public String getTargetModificationDate() {
    return myTarget == null ? "" : getLastModification(myTarget);
  }

  private static String getLastModification(DiffElement file) {
    return SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date(file.getModificationStamp()));
  }

  public static DirDiffElement createChange(@NotNull DiffElement source, @NotNull DiffElement target) {
    return new DirDiffElement(source, target, ElementType.CHANGED, source.getName());
  }

  public static DirDiffElement createSourceOnly(@NotNull DiffElement source) {
    return new DirDiffElement(source, null, ElementType.SOURCE, null);
  }

  public static DirDiffElement createTargetOnly(@NotNull DiffElement target) {
    return new DirDiffElement(null, target, ElementType.TARGET, null);
  }

  public static DirDiffElement createDirElement(DiffElement src, DiffElement trg, String name) {
    return new DirDiffElement(src, trg, ElementType.SEPARATOR, name);
  }

  public ElementType getType() {
    return myType;
  }

  public DiffElement getSource() {
    return mySource;
  }

  public DiffElement getTarget() {
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

  public boolean isSource() {
    return myType == ElementType.SOURCE;
  }

  public boolean isTarget() {
    return myType == ElementType.TARGET;
  }

  public DirDiffOperation getOperation() {
    return myOperation;
  }

  public void setNextOperation() {
    final DirDiffOperation o = myOperation;
    if (isSource()) {
      myOperation = o == COPY_TO ? REMOVE : o == REMOVE ? NONE : COPY_TO;
    } else if (isTarget()) {
      myOperation = o == COPY_FROM ? REMOVE : o == REMOVE ? NONE : COPY_FROM;
    } else {
      myOperation = o == MERGE ? COPY_TO : o == COPY_TO ? COPY_FROM : o == COPY_FROM ? NONE : MERGE;
    }
  }

  public static enum ElementType {SOURCE, TARGET, SEPARATOR, CHANGED}

  public Icon getIcon() {
    return mySource != null ? mySource.getIcon() : myTarget.getIcon();
  }
}
