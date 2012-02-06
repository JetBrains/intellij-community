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

import org.jetbrains.annotations.Nullable;

public enum HgFileStatusEnum {

  ADDED('A'),
  MODIFIED('M'),
  UNVERSIONED('?'),
  MISSING('!'),
  UNMODIFIED('C'),
  DELETED('R'),
  COPY(' '),
  IGNORED('I');

  private final char id;

  private HgFileStatusEnum(char id) {
    this.id = id;
  }

  @Nullable
  public static HgFileStatusEnum parse(char c) {
    for (HgFileStatusEnum status : HgFileStatusEnum.values()) {
      if (status.id == c) {
        return status;
      }
    }
    return null;
  }
}
