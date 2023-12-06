// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class HgChange {

  private @NotNull HgFile beforeFile;
  private @NotNull HgFile afterFile;
  private @NotNull HgFileStatusEnum status;

  public HgChange(@NotNull HgFile hgFile, @NotNull HgFileStatusEnum status) {
    this.beforeFile = hgFile;
    this.afterFile = hgFile;
    this.status = status;
  }

  public @NotNull HgFile beforeFile() {
    return beforeFile;
  }

  public @NotNull HgFile afterFile() {
    return afterFile;
  }

  public @NotNull HgFileStatusEnum getStatus() {
    return status;
  }

  public void setBeforeFile(@NotNull HgFile beforeFile) {
    this.beforeFile = beforeFile;
  }

  public void setAfterFile(@NotNull HgFile afterFile) {
    this.afterFile = afterFile;
  }

  public void setStatus(@NotNull HgFileStatusEnum status) {
    this.status = status;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    HgChange change = (HgChange)o;

    if (!afterFile.equals(change.afterFile)) {
      return false;
    }
    if (!beforeFile.equals(change.beforeFile)) {
      return false;
    }
    if (status != change.status) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(beforeFile, afterFile, status);
  }

  @Override
  public @NonNls String toString() {
    return String.format("HgChange#%s %s => %s", status, beforeFile, afterFile);
  }
}