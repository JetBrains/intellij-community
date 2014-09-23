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
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import java.util.List;

public class AssertExprent extends Exprent {

  private List<Exprent> parameters;

  {
    this.type = EXPRENT_ASSERT;
  }

  public AssertExprent(List<Exprent> parameters) {
    this.parameters = parameters;
  }

  public String toJava(int indent) {

    StringBuilder buffer = new StringBuilder();

    buffer.append("assert ");

    if (parameters.get(0) == null) {
      buffer.append("false");
    }
    else {
      buffer.append(parameters.get(0).toJava(indent));
    }
    if (parameters.size() > 1) {
      buffer.append(" : ");
      buffer.append(parameters.get(1).toJava(indent));
    }

    return buffer.toString();
  }
}
