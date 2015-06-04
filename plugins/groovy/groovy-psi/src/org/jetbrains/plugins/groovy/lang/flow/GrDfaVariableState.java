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
package org.jetbrains.plugins.groovy.lang.flow;

import com.intellij.codeInspection.dataFlow.DfaVariableState;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.value.DfaPsiType;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

public class GrDfaVariableState extends DfaVariableState {

  public enum Truth {
    UNKNOWN,
    FALSE,
    TRUE,
  }

  private Truth myTruth;

  public GrDfaVariableState(@NotNull DfaVariableValue dfaVar) {
    super(dfaVar);
    myTruth = Truth.UNKNOWN;
  }

  public GrDfaVariableState(Set<DfaPsiType> instanceofValues, Set<DfaPsiType> notInstanceofValues, Nullness nullability, Truth truth) {
    super(instanceofValues, notInstanceofValues, nullability);
    myTruth = truth;
  }

  @Override
  protected GrDfaVariableState createCopy(Set<DfaPsiType> instanceofValues, Set<DfaPsiType> notInstanceofValues, Nullness nullability) {
    return new GrDfaVariableState(instanceofValues, notInstanceofValues, nullability, myTruth);
  }

  protected GrDfaVariableState createCopy(Truth truth) {
    final GrDfaVariableState copy = createCopy(getInstanceofValues(), getNotInstanceofValues(), getNullability());
    copy.myTruth = truth;
    return copy;
  }

  @Override
  public boolean isNullable() {
    return super.isNullable() && myTruth != Truth.TRUE;
  }

  @Override
  public boolean isNotNull() {
    return super.isNotNull() || myTruth == Truth.TRUE;
  }

  @Nullable
  public GrDfaVariableState withTruth(Truth truth) {
    return myTruth == truth ? this
                            : myTruth == Truth.UNKNOWN ? createCopy(truth)
                                                       : null;
  }

  @Override
  public int hashCode() {
    return super.hashCode() * 31 + myTruth.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof GrDfaVariableState)) return false;
    return super.equals(obj) && myTruth == ((GrDfaVariableState)obj).myTruth;
  }

  @Override
  public String toString() {
    return super.toString() + ": " + myTruth.toString().toLowerCase(Locale.getDefault());
  }
}
