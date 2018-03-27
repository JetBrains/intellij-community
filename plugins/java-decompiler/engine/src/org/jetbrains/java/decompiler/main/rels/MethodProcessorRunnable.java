// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.rels;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.code.DeadCodeHelper;
import org.jetbrains.java.decompiler.modules.decompiler.*;
import org.jetbrains.java.decompiler.modules.decompiler.deobfuscator.ExceptionDeobfuscator;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;

import java.io.IOException;

public class MethodProcessorRunnable implements Runnable {
  public final Object lock = new Object();

  private final StructMethod method;
  private final MethodDescriptor methodDescriptor;
  private final VarProcessor varProc;

  private volatile RootStatement root;
  private volatile Throwable error;
  private volatile boolean finished = false;

  public MethodProcessorRunnable(StructMethod method, MethodDescriptor methodDescriptor, VarProcessor varProc) {
    this.method = method;
    this.methodDescriptor = methodDescriptor;
    this.varProc = varProc;
  }

  @Override
  public void run() {
    error = null;
    root = null;

    try {
      root = codeToJava(method, methodDescriptor, varProc);
    }
    catch (ThreadDeath ex) {
      throw ex;
    }
    catch (Throwable ex) {
      error = ex;
    }

    finished = true;
    synchronized (lock) {
      lock.notifyAll();
    }
  }

  public static RootStatement codeToJava(StructMethod mt, MethodDescriptor md, VarProcessor varProc) throws IOException {
    StructClass cl = mt.getClassStruct();

    boolean isInitializer = CodeConstants.CLINIT_NAME.equals(mt.getName()); // for now static initializer only

    mt.expandData();
    InstructionSequence seq = mt.getInstructionSequence();
    ControlFlowGraph graph = new ControlFlowGraph(seq);

    DeadCodeHelper.removeDeadBlocks(graph);
    graph.inlineJsr(mt);

    // TODO: move to the start, before jsr inlining
    DeadCodeHelper.connectDummyExitBlock(graph);

    DeadCodeHelper.removeGotos(graph);

    ExceptionDeobfuscator.removeCircularRanges(graph);

    ExceptionDeobfuscator.restorePopRanges(graph);

    if (DecompilerContext.getOption(IFernflowerPreferences.REMOVE_EMPTY_RANGES)) {
      ExceptionDeobfuscator.removeEmptyRanges(graph);
    }

    if (DecompilerContext.getOption(IFernflowerPreferences.NO_EXCEPTIONS_RETURN)) {
      // special case: single return instruction outside of a protected range
      DeadCodeHelper.incorporateValueReturns(graph);
    }

    //		ExceptionDeobfuscator.restorePopRanges(graph);
    ExceptionDeobfuscator.insertEmptyExceptionHandlerBlocks(graph);

    DeadCodeHelper.mergeBasicBlocks(graph);

    DecompilerContext.getCounterContainer().setCounter(CounterContainer.VAR_COUNTER, mt.getLocalVariables());

    if (ExceptionDeobfuscator.hasObfuscatedExceptions(graph)) {
      DecompilerContext.getLogger().writeMessage("Heavily obfuscated exception ranges found!", IFernflowerLogger.Severity.WARN);
    }

    RootStatement root = DomHelper.parseGraph(graph);

    FinallyProcessor fProc = new FinallyProcessor(md, varProc);
    while (fProc.iterateGraph(mt, root, graph)) {
      root = DomHelper.parseGraph(graph);
    }

    // remove synchronized exception handler
    // not until now because of comparison between synchronized statements in the finally cycle
    DomHelper.removeSynchronizedHandler(root);

    //		LabelHelper.lowContinueLabels(root, new HashSet<StatEdge>());

    SequenceHelper.condenseSequences(root);

    ClearStructHelper.clearStatements(root);

    ExprProcessor proc = new ExprProcessor(md, varProc);
    proc.processStatement(root, cl);

    SequenceHelper.condenseSequences(root);

    while (true) {
      StackVarsProcessor stackProc = new StackVarsProcessor();
      stackProc.simplifyStackVars(root, mt, cl);

      varProc.setVarVersions(root);

      if (!new PPandMMHelper().findPPandMM(root)) {
        break;
      }
    }

    while (true) {
      LabelHelper.cleanUpEdges(root);

      while (true) {
        MergeHelper.enhanceLoops(root);

        if (LoopExtractHelper.extractLoops(root)) {
          continue;
        }

        if (!IfHelper.mergeAllIfs(root)) {
          break;
        }
      }

      if (DecompilerContext.getOption(IFernflowerPreferences.IDEA_NOT_NULL_ANNOTATION)) {
        if (IdeaNotNullHelper.removeHardcodedChecks(root, mt)) {
          SequenceHelper.condenseSequences(root);

          StackVarsProcessor stackProc = new StackVarsProcessor();
          stackProc.simplifyStackVars(root, mt, cl);

          varProc.setVarVersions(root);
        }
      }

      LabelHelper.identifyLabels(root);

      if (InlineSingleBlockHelper.inlineSingleBlocks(root)) {
        continue;
      }

      // initializer may have at most one return point, so no transformation of method exits permitted
      if (isInitializer || !ExitHelper.condenseExits(root)) {
        break;
      }

      // FIXME: !!
      //			if(!EliminateLoopsHelper.eliminateLoops(root)) {
      //				break;
      //			}
    }

    ExitHelper.removeRedundantReturns(root);

    SecondaryFunctionsHelper.identifySecondaryFunctions(root, varProc);

    varProc.setVarDefinitions(root);

    // must be the last invocation, because it makes the statement structure inconsistent
    // FIXME: new edge type needed
    LabelHelper.replaceContinueWithBreak(root);

    mt.releaseResources();

    return root;
  }

  public RootStatement getResult() throws Throwable {
    Throwable t = error;
    if (t != null) throw t;
    return root;
  }

  public boolean isFinished() {
    return finished;
  }
}