// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.main.rels;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.CancellationManager;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.collectors.LimitContainer;
import org.jetbrains.java.decompiler.main.collectors.VarNamesCollector;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersion;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructMethodParametersAttribute;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClassWrapper {
  private final StructClass classStruct;
  private final Set<String> hiddenMembers = new HashSet<>();
  private final VBStyleCollection<Exprent, String> staticFieldInitializers = new VBStyleCollection<>();
  private final VBStyleCollection<Exprent, String> dynamicFieldInitializers = new VBStyleCollection<>();
  private final VBStyleCollection<MethodWrapper, String> methods = new VBStyleCollection<>();

  public ClassWrapper(StructClass classStruct) {
    this.classStruct = classStruct;
  }

  public void init() {
    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS, classStruct);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_WRAPPER, this);
    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS_NODE, DecompilerContext.getClassProcessor().getMapRootClasses().get(classStruct.qualifiedName));
    DecompilerContext.getLogger().startClass(classStruct.qualifiedName);

    boolean testMode = DecompilerContext.getOption(IFernflowerPreferences.UNIT_TEST_MODE);
    CancellationManager cancellationManager = DecompilerContext.getCancellationManager();
    for (StructMethod mt : classStruct.getMethods()) {
      DecompilerContext.getLogger().startMethod(mt.getName() + " " + mt.getDescriptor());

      MethodDescriptor md = MethodDescriptor.parseDescriptor(mt, null);
      VarProcessor varProc = new VarProcessor(classStruct, mt, md);
      DecompilerContext.startMethod(varProc);

      VarNamesCollector vc = varProc.getVarNamesCollector();
      CounterContainer counter = DecompilerContext.getCounterContainer();

      RootStatement root = null;

      boolean isError = false;
      String customErrorMessage = null;
      try {
        cancellationManager.checkCanceled();
        if (mt.containsCode()) {
          if (testMode) {
            root = MethodProcessorRunnable.codeToJava(classStruct, mt, md, varProc);
          }
          else {
            DecompilerContext context = DecompilerContext.getCurrentContext();
            try {
              cancellationManager.startMethod(classStruct.qualifiedName, mt.getName());
              MethodProcessorRunnable mtProc =
                new MethodProcessorRunnable(classStruct, mt, md, varProc, DecompilerContext.getCurrentContext());
              mtProc.run();
              cancellationManager.checkCanceled();
              root = mtProc.getResult();
            }
            finally {
              DecompilerContext.setCurrentContext(context);
              cancellationManager.finishMethod(classStruct.qualifiedName, mt.getName());
            }
          }
        }
        else {
          int varIndex = 0;
          if (!mt.hasModifier(CodeConstants.ACC_STATIC)) {
            varProc.getThisVars().put(new VarVersion(0, 0), classStruct.qualifiedName);
            varProc.setVarName(new VarVersion(0, 0), vc.getFreeName(0));
            varIndex = 1;
          }
          for (int i = 0; i < md.params.length; i++) {
            varProc.setVarName(new VarVersion(varIndex, 0), vc.getFreeName(varIndex));
            varIndex += md.params[i].getStackSize();
          }
        }
      }
      catch (LimitContainer.LimitExceededDecompilerException e) {
        String message =
          "Method " + mt.getName() + " " + mt.getDescriptor() + " in class " + classStruct.qualifiedName + " couldn't be decompiled.";
        DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN, e);
        isError = true;
        customErrorMessage = e.getMessage();
      }
      catch (CancellationManager.TimeExceedException e) {
        String message = "Processing time limit exceeded for method " + mt.getName() + ", execution interrupted.";
        DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.ERROR);
        isError = true;
      }
      catch (CancellationManager.CanceledException e) {
        throw e;
      }
      catch (Throwable t) {
        String message =
          "Method " + mt.getName() + " " + mt.getDescriptor() + " in class " + classStruct.qualifiedName + " couldn't be decompiled.";
        DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN, t);
        isError = true;
      }

      MethodWrapper methodWrapper = new MethodWrapper(root, varProc, mt, counter);
      methodWrapper.decompiledWithErrors = isError;
      methodWrapper.decompiledWithErrorsMessage = customErrorMessage;

      methods.addWithKey(methodWrapper, InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));

      if (!isError) {
        // rename vars so that no one has the same name as a field
        VarNamesCollector namesCollector = new VarNamesCollector();
        classStruct.getFields().forEach(f -> namesCollector.addName(f.getName()));
        varProc.refreshVarNames(namesCollector);

        applyParameterNames(mt, md, varProc);  // if parameter names are present and should be used

        applyDebugInfo(mt, varProc, methodWrapper);  // if debug information is present and should be used
      }

      DecompilerContext.getLogger().endMethod();
    }

    DecompilerContext.getLogger().endClass();
  }

  private static void applyParameterNames(StructMethod mt, MethodDescriptor md, VarProcessor varProc) {
    if (DecompilerContext.getOption(IFernflowerPreferences.USE_METHOD_PARAMETERS)) {
      StructMethodParametersAttribute attr = mt.getAttribute(StructGeneralAttribute.ATTRIBUTE_METHOD_PARAMETERS);
      if (attr != null) {
        List<StructMethodParametersAttribute.Entry> entries = attr.getEntries();
        int index = varProc.getFirstParameterVarIndex();
        for (int i = varProc.getFirstParameterPosition(); i < entries.size(); i++) {
          StructMethodParametersAttribute.Entry entry = entries.get(i);
          if (entry.myName != null) {
            varProc.setVarName(new VarVersion(index, 0), entry.myName);
          }
          if ((entry.myAccessFlags & CodeConstants.ACC_FINAL) != 0) {
            varProc.setParameterFinal(new VarVersion(index, 0));
          }
          index += md.params[i].getStackSize();
        }
      }
    }
  }

  private static void applyDebugInfo(StructMethod mt, VarProcessor varProc, MethodWrapper methodWrapper) {
    // Only rename parameters in the var processor if we aren't already renaming them with JAD naming
    if (DecompilerContext.getOption(IFernflowerPreferences.USE_DEBUG_VAR_NAMES) && (!DecompilerContext.getOption(IFernflowerPreferences.USE_JAD_VARNAMING) || !DecompilerContext.getOption(IFernflowerPreferences.USE_JAD_PARAMETER_RENAMING))) {
      StructLocalVariableTableAttribute attr = mt.getLocalVariableAttr();
      if (attr != null) {
        // only param names here
        varProc.setDebugVarNames(attr.getMapNames());
      }
    }
  }

  public MethodWrapper getMethodWrapper(String name, String descriptor) {
    return methods.getWithKey(InterpreterUtil.makeUniqueKey(name, descriptor));
  }

  public StructClass getClassStruct() {
    return classStruct;
  }

  public VBStyleCollection<MethodWrapper, String> getMethods() {
    return methods;
  }

  public Set<String> getHiddenMembers() {
    return hiddenMembers;
  }

  public VBStyleCollection<Exprent, String> getStaticFieldInitializers() {
    return staticFieldInitializers;
  }

  public VBStyleCollection<Exprent, String> getDynamicFieldInitializers() {
    return dynamicFieldInitializers;
  }

  @Override
  public String toString() {
    return classStruct.qualifiedName;
  }
}
