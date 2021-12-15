// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.*;
import org.jetbrains.java.decompiler.modules.decompiler.stats.SwitchStatement;

import java.util.*;

import static org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;

public final class SwitchHelper {

  /**
   * Makes simplifying transformation on <code>switchStatement</code>,
   * e.g. bind switch case values with enum fields for switches by enums.
   *
   * @param switchStatement statement to transform
   */
  public static void simplify(@NotNull SwitchStatement switchStatement) {
    SwitchExprent switchExprent = (SwitchExprent)switchStatement.getHeadExprent();
    Exprent value = Objects.requireNonNull(switchExprent).getValue();
    if (!isEnumArray(value)) return;
    List<List<@Nullable Exprent>> caseValues = switchStatement.getCaseValues();
    ArrayExprent array = (ArrayExprent)value;
    Map<Exprent, Exprent> mapping = evaluateCaseLabelsToFieldsMapping(caseValues, array);
    List<List<@Nullable Exprent>> realCaseValues = findRealCaseValues(caseValues, mapping);
    if (realCaseValues == null) return;
    caseValues.clear();
    caseValues.addAll(realCaseValues);
    switchExprent.replaceExprent(value, ((InvocationExprent)array.getIndex()).getInstance().copy());
  }

  @NotNull
  private static Map<Exprent, Exprent> evaluateCaseLabelsToFieldsMapping(List<List<Exprent>> caseValues, ArrayExprent array) {
    Map<Exprent, Exprent> mapping = new HashMap<>(caseValues.size());
    FieldExprent arrayField = (FieldExprent)array.getArray();
    ClassesProcessor.ClassNode classNode = DecompilerContext.getClassProcessor().getMapRootClasses().get(arrayField.getClassname());
    if (classNode == null) return mapping;
    MethodWrapper wrapper = classNode.getWrapper().getMethodWrapper(CodeConstants.CLINIT_NAME, "()V");
    if (wrapper != null && wrapper.root != null) {
      wrapper.getOrBuildGraph().iterateExprents(exprent -> {
        if (exprent instanceof AssignmentExprent) {
          AssignmentExprent assignment = (AssignmentExprent)exprent;
          Exprent left = assignment.getLeft();
          if (left.type == Exprent.EXPRENT_ARRAY && ((ArrayExprent)left).getArray().equals(arrayField)) {
            mapping.put(assignment.getRight(), ((InvocationExprent)((ArrayExprent)left).getIndex()).getInstance());
          }
        }
        return 0;
      });
    }
    return mapping;
  }

  @Nullable
  private static List<List<@Nullable Exprent>> findRealCaseValues(@NotNull List<List<Exprent>> caseValues,
                                                                  @NotNull Map<Exprent, Exprent> mapping) {
    List<List<@Nullable Exprent>> result = new ArrayList<>(caseValues.size());
    for (List<Exprent> caseValue : caseValues) {
      List<@Nullable Exprent> values = new ArrayList<>(caseValue.size());
      result.add(values);
      for (Exprent exprent : caseValue) {
        if (exprent == null) {
          values.add(null);
        }
        else {
          Exprent realConst = mapping.get(exprent);
          if (realConst == null) {
            DecompilerContext.getLogger()
              .writeMessage("Unable to simplify switch on enum: " + exprent + " not found, available: " + mapping, Severity.ERROR);
            return null;
          }
          values.add(realConst.copy());
        }
      }
    }
    return result;
  }

  private static boolean isEnumArray(Exprent exprent) {
    if (exprent instanceof ArrayExprent) {
      Exprent field = ((ArrayExprent)exprent).getArray();
      Exprent index = ((ArrayExprent)exprent).getIndex();
      return field instanceof FieldExprent &&
             (((FieldExprent)field).getName().startsWith("$SwitchMap") ||
              (index instanceof InvocationExprent && ((InvocationExprent)index).getName().equals("ordinal")));
    }
    return false;
  }
}
