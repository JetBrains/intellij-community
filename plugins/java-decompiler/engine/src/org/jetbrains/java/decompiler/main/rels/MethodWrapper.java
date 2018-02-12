// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.rels;

import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructMethod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MethodWrapper {
  public final RootStatement root;
  public final VarProcessor varproc;
  public final StructMethod methodStruct;
  public final CounterContainer counter;
  public final Set<String> setOuterVarNames = new HashSet<>();

  public DirectGraph graph;
  public List<VarVersionPair> synthParameters;
  public boolean decompiledWithErrors;

  public MethodWrapper(RootStatement root, VarProcessor varproc, StructMethod methodStruct, CounterContainer counter) {
    this.root = root;
    this.varproc = varproc;
    this.methodStruct = methodStruct;
    this.counter = counter;
  }

  public DirectGraph getOrBuildGraph() {
    if (graph == null && root != null) {
      graph = new FlattenStatementsHelper().buildDirectGraph(root);
    }
    return graph;
  }

  @Override
  public String toString() {
    return methodStruct.getName();
  }
}