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
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.openapi.diff.impl.dir.DirDiffOperation.*;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffElement {
  private DTree myParent;
  private DType myType;
  private DiffElement mySource;
  private long mySourceLength;
  private DiffElement myTarget;
  private long myTargetLength;
  private String myName;
  private DirDiffOperation myOperation;
  private DirDiffOperation myDefaultOperation;
  private DTree myNode;

  private DirDiffElement(DTree parent, @Nullable DiffElement source, @Nullable DiffElement target, DType type, String name) {
    myParent = parent.getParent();
    myNode = parent;
    myType = type;
    mySource = source;
    mySourceLength = source == null || source.isContainer() ? -1 : source.getSize();
    myTarget = target;
    myTargetLength = target == null || target.isContainer() ? -1 : target.getSize();
    myName = name;
    if (type == DType.ERROR) {
      myDefaultOperation = NONE;
    }
    else if (isSource()) {
      myDefaultOperation = COPY_TO;
    }
    else if (isTarget()) {
      myDefaultOperation = COPY_FROM;
    }
    else if (type == DType.EQUAL) {
      myDefaultOperation = EQUAL;
    }
    else if (type == DType.CHANGED) {
      assert source != null;
      myDefaultOperation = MERGE;
    }
  }

  public String getSourceModificationDate() {
    return mySource == null ? "" : getLastModification(mySource);
  }

  public String getTargetModificationDate() {
    return myTarget == null ? "" : getLastModification(myTarget);
  }

  public void updateTargetData() {
    if (myTarget != null && !myTarget.isContainer()) {
      myTargetLength = myTarget.getSize();
    } else {
      myTargetLength = -1;
    }
  }

  private static String getLastModification(DiffElement file) {
    final long timeStamp = file.getTimeStamp();
    return timeStamp < 0 ? "" : DateFormatUtil.formatDateTime(timeStamp);
  }

  public static DirDiffElement createChange(DTree parent, @NotNull DiffElement source, @NotNull DiffElement target) {
    return new DirDiffElement(parent, source, target, DType.CHANGED, source.getName());
  }

  public static DirDiffElement createError(DTree parent, @Nullable DiffElement source, @Nullable DiffElement target) {
    return new DirDiffElement(parent, source, target, DType.ERROR, source == null ? target.getName() : source.getName());
  }

  public static DirDiffElement createSourceOnly(DTree parent, @NotNull DiffElement source) {
    return new DirDiffElement(parent, source, null, DType.SOURCE, null);
  }

  public static DirDiffElement createTargetOnly(DTree parent, @NotNull DiffElement target) {
    return new DirDiffElement(parent, null, target, DType.TARGET, null);
  }

  public static DirDiffElement createDirElement(DTree parent, DiffElement src, DiffElement trg, String name) {
    return new DirDiffElement(parent, src, trg, DType.SEPARATOR, name);
  }

  public static DirDiffElement createEqual(DTree parent, @NotNull DiffElement source, @NotNull DiffElement target) {
    return new DirDiffElement(parent, source, target, DType.EQUAL, source.getName());
  }

  public DType getType() {
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
    return myType == DType.CHANGED || myType == DType.SOURCE || myType == DType.EQUAL
           ? mySource.getName() : mySource == null ? null : mySource.getName();
  }

  @Nullable
  public String getSourceSize() {
    return mySourceLength < 0 ? null : String.valueOf(mySourceLength);
  }

  @Nullable
  public String getTargetName() {
    return myType == DType.CHANGED || myType == DType.TARGET || myType == DType.EQUAL
           ? myTarget.getName() : myTarget == null ? null : myTarget.getName();
  }

  @Nullable
  public String getTargetSize() {
    return myTargetLength < 0 ? null : String.valueOf(myTargetLength);
  }

  public boolean isSeparator() {
    return myType == DType.SEPARATOR;
  }

  public boolean isSource() {
    return myType == DType.SOURCE;
  }

  public boolean isTarget() {
    return myType == DType.TARGET;
  }

  public DirDiffOperation getOperation() {
    return myOperation == null ? myDefaultOperation : myOperation;
  }

  public void updateSourceFromTarget(DiffElement target) {
    myTarget = target;
    myTargetLength = mySourceLength;
    myDefaultOperation = DirDiffOperation.EQUAL;
    myOperation = DirDiffOperation.EQUAL;
    myType = DType.EQUAL;
  }

  public void updateTargetFromSource(DiffElement source) {
    mySource = source;
    mySourceLength = myTargetLength;
    myDefaultOperation = DirDiffOperation.EQUAL;
    myOperation = DirDiffOperation.EQUAL;
    myType = DType.EQUAL;
  }

  public void setNextOperation() {
    final DirDiffOperation op = getOperation();
    if (myType == DType.SOURCE) {
      myOperation = op == COPY_TO ? DELETE : op == DELETE ? NONE : COPY_TO;
    } else if (myType == DType.TARGET) {
      myOperation = op == COPY_FROM ? DELETE : op == DELETE ? NONE : COPY_FROM;
    } else if (myType == DType.CHANGED) {
      myOperation = op == MERGE ? COPY_FROM : op == COPY_FROM ? COPY_TO : MERGE;
    }
  }

  public void setOperation(@NotNull DirDiffOperation operation) {
    if (myType == DType.EQUAL || myType == DType.SEPARATOR) return;
    if (myType == DType.TARGET && operation == COPY_TO) return;
    if (myType == DType.SOURCE && operation == COPY_FROM) return;
    if (myType == DType.CHANGED && operation == DELETE) return;

    myOperation = operation;
  }

  public Icon getIcon() {
    return mySource != null ? mySource.getIcon() : myTarget.getIcon();
  }

  public DTree getParentNode() {
    return myParent;
  }

  public DTree getNode() {
    return myNode;
  }
}
