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
package org.jetbrains.java.decompiler.modules.decompiler.sforms;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;

import java.util.ArrayList;
import java.util.List;


public class DirectNode {

  public static final int NODE_DIRECT = 1;
  public static final int NODE_TAIL = 2;
  public static final int NODE_INIT = 3;
  public static final int NODE_CONDITION = 4;
  public static final int NODE_INCREMENT = 5;
  public static final int NODE_TRY = 6;

  public final int type;

  public final String id;

  public BasicBlockStatement block;

  public final Statement statement;

  public List<Exprent> exprents = new ArrayList<>();

  public final List<DirectNode> succs = new ArrayList<>();

  public final List<DirectNode> preds = new ArrayList<>();

  public DirectNode(int type, Statement statement, String id) {
    this.type = type;
    this.statement = statement;
    this.id = id;
  }

  public DirectNode(int type, Statement statement, BasicBlockStatement block) {
    this.type = type;
    this.statement = statement;

    this.id = block.id.toString();
    this.block = block;
  }

  @Override
  public String toString() {
    return id;
  }
}
