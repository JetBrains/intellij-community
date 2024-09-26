// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassWriter;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersion;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute.LocalVariable;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTypeTableAttribute;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericFieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericMain;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericType;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;
import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class VarExprent extends Exprent {
  public static final int STACK_BASE = 10000;
  public static final String VAR_NAMELESS_ENCLOSURE = "<VAR_NAMELESS_ENCLOSURE>";

  private int index;
  private VarType varType;
  private boolean definition = false;
  private final VarProcessor processor;
  private int version = 0;
  private boolean classDef = false;
  private boolean stack = false;
  private LocalVariable lvtItem = null;

  public VarExprent(int index, VarType varType, VarProcessor processor) {
    this(index, varType, processor, null);
  }

  public VarExprent(int index, VarType varType, VarProcessor processor, BitSet bytecode) {
    super(EXPRENT_VAR);
    this.index = index;
    this.varType = varType;
    this.processor = processor;
    this.addBytecodeOffsets(bytecode);
  }

  @Override
  public VarType getExprType() {
    return getVarType();
  }

  @Override
  public VarType getInferredExprType(VarType upperBound) {
    if (lvtItem != null && lvtItem.getSignature() != null) {
      // TODO; figure out why it's crashing, ugly fix for now
      try {
        return GenericType.parse(lvtItem.getSignature());
      } catch (StringIndexOutOfBoundsException ex) {
        DecompilerContext.getLogger().writeMessage("Inconsistent data: ",
                                                   IFernflowerLogger.Severity.WARN, ex);
      }
    }
    else if (lvtItem != null) {
      return lvtItem.getVarType();
    }
    return getVarType();
  }

  @Override
  public int getExprentUse() {
    return Exprent.MULTIPLE_USES | Exprent.SIDE_EFFECTS_FREE;
  }

  @Override
  public List<Exprent> getAllExprents(List<Exprent> lst) {
    return lst;
  }

  @Override
  public Exprent copy() {
    VarExprent var = new VarExprent(index, getVarType(), processor, bytecode);
    var.setDefinition(definition);
    var.setVersion(version);
    var.setClassDef(classDef);
    var.setStack(stack);
    var.setLVT(lvtItem);
    return var;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buffer = new TextBuffer();

    tracer.addMapping(bytecode);

    if (classDef) {
      ClassNode child = DecompilerContext.getClassProcessor().getMapRootClasses().get(varType.getValue());
      new ClassWriter().classToJava(child, buffer, indent, tracer);
      tracer.incrementCurrentSourceLine(buffer.countLines());
    }
    else {
      VarVersion varVersion = getVarVersionPair();

      if (definition) {
        if (processor != null && processor.getVarFinal(varVersion) == VarProcessor.VAR_EXPLICIT_FINAL) {
          buffer.append("final ");
        }
        appendDefinitionType(buffer);
        buffer.append(" ");
      }

      buffer.append(getName());
    }

    return buffer;
  }

  public int getVisibleOffset() {
    return bytecode == null ? -1 : bytecode.length();
  }

  @NotNull
  public static String getName(VarVersion versionPair) {
    return "var" + versionPair.var + (versionPair.version == 0 ? "" : "_" + versionPair.version);
  }

  public VarVersion getVarVersionPair() {
    return new VarVersion(index, version);
  }

  public VarType getDefinitionType() {
    if (DecompilerContext.getOption(IFernflowerPreferences.USE_DEBUG_VAR_NAMES)) {

      if (lvtItem != null) {
        if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
          if (lvtItem.getSignature() != null) {
            GenericFieldDescriptor descriptor = GenericMain.parseFieldSignature(lvtItem.getSignature());
            if (descriptor != null) {
              return descriptor.type;
            }
          }
        }
        return getVarType();
      }

      MethodWrapper method = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
      if (method != null) {
        Integer originalIndex = null;
        if (processor != null) {
          originalIndex = processor.getVarOriginalIndex(index);
        }
        int visibleOffset = bytecode == null ? -1 : bytecode.length();
        if (originalIndex != null) {
          // first try from signature
          if (DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
            StructLocalVariableTypeTableAttribute attr =
              method.methodStruct.getAttribute(StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TYPE_TABLE);
            if (attr != null) {
              String signature = attr.getSignature(originalIndex, visibleOffset);
              if (signature != null) {
                GenericFieldDescriptor descriptor = GenericMain.parseFieldSignature(signature);
                if (descriptor != null) {
                  return descriptor.type;
                }
              }
            }
          }

          // then try from descriptor
          StructLocalVariableTableAttribute attr = method.methodStruct.getLocalVariableAttr();
          if (attr != null) {
            String descriptor = attr.getDescriptor(originalIndex, visibleOffset);
            if (descriptor != null) {
              return new VarType(descriptor);
            }
          }
        }
      }
    }
    return getVarType();
  }

  void appendDefinitionType(TextBuffer buffer) {
    buffer.append(ExprProcessor.getCastTypeName(getDefinitionType(), Collections.emptyList()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, version);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof VarExprent ve)) return false;

    return index == ve.getIndex() &&
           version == ve.getVersion() &&
           Objects.equals(getVarType(), ve.getVarType()); // FIXME: varType comparison redundant?
  }

  @Override
  public void fillBytecodeRange(@Nullable BitSet values) {
    measureBytecode(values);
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public VarType getVarType() {
    if (DecompilerContext.getOption(IFernflowerPreferences.USE_DEBUG_VAR_NAMES) && lvtItem != null) {
      return new VarType(lvtItem.getDescriptor());
    }

    VarType vt = null;
    if (processor != null) {
      vt = processor.getVarType(getVarVersionPair());
    }

    if (vt == null || (varType != null && varType.getType() != CodeConstants.TYPE_UNKNOWN)) {
      vt = varType;
    }

    return vt == null ? VarType.VARTYPE_UNKNOWN : vt;
  }

  public void setVarType(VarType varType) {
    this.varType = varType;
  }

  public boolean isDefinition() {
    return definition;
  }

  public void setDefinition(boolean definition) {
    this.definition = definition;
  }

  public VarProcessor getProcessor() {
    return processor;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public boolean isClassDef() {
    return classDef;
  }

  public void setClassDef(boolean classDef) {
    this.classDef = classDef;
  }

  public boolean isStack() {
    return stack;
  }

  public void setStack(boolean stack) {
    this.stack = stack;
  }

  public void setLVT(LocalVariable var) {
    this.lvtItem = var;
    if (processor != null && lvtItem != null) {
      processor.setVarType(getVarVersionPair(), lvtItem.getVarType());
    }
  }

  /**
   * Retrieves the local variable item from local variable table
   *
   * @return the local variable item of type LocalVariable.
   */
  public LocalVariable getLVItem() {
    return lvtItem;
  }

  public String getName() {
    VarVersion pair = getVarVersionPair();
    if (lvtItem != null && TextUtil.isValidIdentifier(lvtItem.getName(), CodeConstants.BYTECODE_JAVA_22))
      return lvtItem.getName();

    if (processor != null) {
      String ret = processor.getVarName(pair);
      if (ret != null)
        return ret;
    }

    return getName(pair);
  }

  @Override
  public CheckTypesResult checkExprTypeBounds() {
    if (lvtItem != null) {
      CheckTypesResult ret = new CheckTypesResult();
      ret.addMinTypeExprent(this, lvtItem.getVarType());
      return ret;
    }
    return null;
  }

  public boolean isVarReferenced(Statement stat, VarExprent... whitelist) {
    if (stat.getExprents() == null) {
      for (Object obj : stat.getSequentialObjects()) {
        if (obj instanceof Statement) {
          if (isVarReferenced((Statement)obj, whitelist)) {
            return true;
          }
        }
        else if (obj instanceof Exprent) {
          if (isVarReferenced((Exprent)obj, whitelist)) {
            return true;
          }
        }
      }
    }
    else {
      for (Exprent exp : stat.getExprents()) {
        if (isVarReferenced(exp, whitelist)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isVarReferenced(Exprent exp, VarExprent... whitelist) {
    List<Exprent> lst = exp.getAllExprents(true);
    lst.add(exp);
    lst = lst.stream().filter(e -> e != this && e.type == Exprent.EXPRENT_VAR &&
      getVarVersionPair().equals(((VarExprent)e).getVarVersionPair()))
        .collect(Collectors.toList());

    for (Exprent var : lst) {
      boolean allowed = false;
      for (VarExprent white : whitelist) {
        if (var == white) {
          allowed = true;
          break;
        }
      }
      if (!allowed) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "VarExprent[" + index + ',' + version +"]";
  }

  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************

  @Override
  public boolean match(MatchNode matchNode, MatchEngine engine) {
    if (!super.match(matchNode, engine)) {
      return false;
    }

    RuleValue rule = matchNode.getRules().get(MatchProperties.EXPRENT_VAR_INDEX);
    if (rule != null) {
      if (rule.isVariable()) {
        return engine.checkAndSetVariableValue((String)rule.value, this.index);
      }
      else {
        return this.index == Integer.parseInt((String)rule.value);
      }
    }

    return true;
  }
}