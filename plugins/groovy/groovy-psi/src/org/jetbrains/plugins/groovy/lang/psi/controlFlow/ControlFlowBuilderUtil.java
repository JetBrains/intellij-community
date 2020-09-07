// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ObjectIntHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GrBlockLambdaBody;
import org.jetbrains.plugins.groovy.lang.psi.api.GrInExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.readWrite.ReadBeforeWriteInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.readWrite.ReadBeforeWriteSemilattice;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.readWrite.ReadBeforeWriteState;

import java.util.*;

/**
 * @author ven
 */
public final class ControlFlowBuilderUtil {
  private ControlFlowBuilderUtil() {
  }

  private static @NotNull Pair<@Nullable ReadBeforeWriteState, @NotNull TObjectIntHashMap<VariableDescriptor>>
  getLastReadBeforeWriteState(Instruction[] flow, boolean onlyFirstRead) {
    TObjectIntHashMap<VariableDescriptor> index = buildVariablesIndex(flow);
    DFAEngine<ReadBeforeWriteState> engine = new DFAEngine<>(
      flow,
      new ReadBeforeWriteInstance(index, onlyFirstRead),
      ReadBeforeWriteSemilattice.INSTANCE
    );
    List<ReadBeforeWriteState> dfaResult = engine.performDFAWithTimeout();
    ReadBeforeWriteState lastState = dfaResult == null ? null : dfaResult.get(dfaResult.size() - 1);
    return Pair.create(lastState, index);
  }

  public static ReadWriteVariableInstruction[] getReadsWithoutPriorWrites(Instruction[] flow, boolean onlyFirstRead) {
    ReadBeforeWriteState lastState = getLastReadBeforeWriteState(flow, onlyFirstRead).first;
    if (lastState == null) {
      return null;
    }
    BitSet reads = lastState.getReads();
    ArrayList<ReadWriteVariableInstruction> result = new ArrayList<>();
    for (int i = reads.nextSetBit(0); i >= 0; i = reads.nextSetBit(i + 1)) {
      if (i == Integer.MAX_VALUE) break;
      result.add((ReadWriteVariableInstruction)flow[i]);
    }
    return result.toArray(ReadWriteVariableInstruction.EMPTY_ARRAY);
  }

  public static @NotNull Set<@NotNull VariableDescriptor> getDescriptorsWithoutWrites(Instruction @NotNull [] flow) {
    Pair<ReadBeforeWriteState, TObjectIntHashMap<VariableDescriptor>> dfaResult = getLastReadBeforeWriteState(flow, true);
    ReadBeforeWriteState lastState = dfaResult.first;
    if (lastState == null) {
      return Collections.emptySet();
    }
    BitSet reads = lastState.getReads();
    BitSet writes = lastState.getWrites();
    Set<VariableDescriptor> result = new HashSet<>();
    for (int i = reads.nextSetBit(0); i >= 0; i = reads.nextSetBit(i + 1)) {
      if (i == Integer.MAX_VALUE) break;
      result.add(((ReadWriteVariableInstruction)flow[i]).getDescriptor());
    }
    TObjectIntHashMap<VariableDescriptor> index = dfaResult.second;
    index.forEachEntry((descriptor, id) -> {
      if (!writes.get(id)) {
        result.add(descriptor);
      }
      return true;
    });
    return result;
  }

  private static TObjectIntHashMap<VariableDescriptor> buildVariablesIndex(Instruction[] flow) {
    TObjectIntHashMap<VariableDescriptor> variablesIndex = new ObjectIntHashMap<>();
    int idx = 0;
    for (Instruction instruction : flow) {
      if (instruction instanceof ReadWriteVariableInstruction) {
        VariableDescriptor descriptor = ((ReadWriteVariableInstruction)instruction).getDescriptor();
        if (!variablesIndex.contains(descriptor)) {
          variablesIndex.put(descriptor, idx++);
        }
      }
    }
    return variablesIndex;
  }

  public static boolean isInstanceOfBinary(GrBinaryExpression binary) {
    if (binary instanceof GrInExpression) {
      GrExpression left = binary.getLeftOperand();
      GrExpression right = binary.getRightOperand();
      if (left instanceof GrReferenceExpression && ((GrReferenceExpression)left).getQualifier() == null &&
          right instanceof GrReferenceExpression && findClassByText((GrReferenceExpression)right)) {
        return true;
      }
    }
    return false;
  }

  private static boolean findClassByText(GrReferenceExpression ref) {
    final String text = ref.getText();
    final int i = text.indexOf('<');
    String className = i == -1 ? text : text.substring(0, i);

    PsiClass[] names = PsiShortNamesCache.getInstance(ref.getProject()).getClassesByName(className, ref.getResolveScope());
    if (names.length > 0) return true;

    PsiFile file = ref.getContainingFile();
    if (file instanceof GroovyFile) {
      GrImportStatement[] imports = ((GroovyFile)file).getImportStatements();
      for (GrImportStatement anImport : imports) {
        if (className.equals(anImport.getImportedName())) return true;
      }
    }

    return false;
  }

  /**
   * check whether statement is return (the statement which provides return value) statement of method or closure.
   *
   * @param st
   * @return
   */
  public static boolean isCertainlyReturnStatement(GrStatement st) {
    final PsiElement parent = st.getParent();
    if (parent instanceof GrOpenBlock) {
      if (st != ArrayUtil.getLastElement(((GrOpenBlock)parent).getStatements())) return false;

      PsiElement pparent = parent.getParent();
      if (pparent instanceof GrMethod) {
        return true;
      }

      if (pparent instanceof GrBlockStatement || pparent instanceof GrCatchClause || pparent instanceof GrLabeledStatement) {
        pparent = pparent.getParent();
      }
      if (pparent instanceof GrControlStatement || pparent instanceof GrTryCatchStatement) {
        return isCertainlyReturnStatement((GrStatement)pparent);
      }
    }

    else if (parent instanceof GrClosableBlock || parent instanceof GrBlockLambdaBody) {
      return st == ArrayUtil.getLastElement(((GrCodeBlock)parent).getStatements());
    }

    else if (parent instanceof GroovyFileBase) {
      return st == ArrayUtil.getLastElement(((GroovyFileBase)parent).getStatements());
    }

    else if (parent instanceof GrForStatement ||
             parent instanceof GrIfStatement && st != ((GrIfStatement)parent).getCondition() ||
             parent instanceof GrSynchronizedStatement && st != ((GrSynchronizedStatement)parent).getMonitor() ||
             parent instanceof GrWhileStatement && st != ((GrWhileStatement)parent).getCondition() ||
             parent instanceof GrConditionalExpression && st != ((GrConditionalExpression)parent).getCondition() ||
             parent instanceof GrElvisExpression) {
      return isCertainlyReturnStatement((GrStatement)parent);
    }

    else if (parent instanceof GrCaseSection) {
      final GrStatement[] statements = ((GrCaseSection)parent).getStatements();
      final GrStatement last = ArrayUtil.getLastElement(statements);
      final GrSwitchStatement switchStatement = (GrSwitchStatement)parent.getParent();

      if (last instanceof GrBreakStatement && statements.length > 1 && statements[statements.length - 2] == st) {
        return isCertainlyReturnStatement(switchStatement);
      }
      else if (st == last) {
        if (st instanceof GrBreakStatement || isLastStatementInCaseSection((GrCaseSection)parent, switchStatement)) {
          return isCertainlyReturnStatement(switchStatement);
        }
      }
    }
    return false;
  }

  private static boolean isLastStatementInCaseSection(GrCaseSection caseSection, GrSwitchStatement switchStatement) {
    final GrCaseSection[] sections = switchStatement.getCaseSections();
    final int i = ArrayUtilRt.find(sections, caseSection);
    if (i == sections.length - 1) {
      return true;
    }

    for (int j = i + 1; j < sections.length; j++) {
      GrCaseSection section = sections[j];
      for (GrStatement statement : section.getStatements()) {
        if (!(statement instanceof GrBreakStatement)) {
          return false;
        }
      }
    }
    return true;
  }
}
