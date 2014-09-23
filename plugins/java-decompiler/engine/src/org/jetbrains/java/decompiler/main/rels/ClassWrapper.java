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
package org.jetbrains.java.decompiler.main.rels;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.collectors.VarNamesCollector;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPaar;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ClassWrapper {

  private StructClass classStruct;
  private Set<String> hiddenMembers = new HashSet<String>();
  private VBStyleCollection<Exprent, String> staticFieldInitializers = new VBStyleCollection<Exprent, String>();
  private VBStyleCollection<Exprent, String> dynamicFieldInitializers = new VBStyleCollection<Exprent, String>();
  private VBStyleCollection<MethodWrapper, String> methods = new VBStyleCollection<MethodWrapper, String>();


  public ClassWrapper(StructClass classStruct) {
    this.classStruct = classStruct;
  }

  public void init() throws IOException {

    DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS, classStruct);

    DecompilerContext.getLogger().startClass(classStruct.qualifiedName);

    // collect field names
    HashSet<String> setFieldNames = new HashSet<String>();
    for (StructField fd : classStruct.getFields()) {
      setFieldNames.add(fd.getName());
    }

    int maxsec = Integer.parseInt(DecompilerContext.getProperty(IFernflowerPreferences.MAX_PROCESSING_METHOD).toString());

    for (StructMethod mt : classStruct.getMethods()) {

      DecompilerContext.getLogger().startMethod(mt.getName() + " " + mt.getDescriptor());

      VarNamesCollector vc = new VarNamesCollector();
      DecompilerContext.setVarNamesCollector(vc);

      CounterContainer counter = new CounterContainer();
      DecompilerContext.setCounterContainer(counter);

      DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD, mt);
      DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_DESCRIPTOR, MethodDescriptor.parseDescriptor(mt.getDescriptor()));

      VarProcessor varproc = new VarProcessor();
      DecompilerContext.setProperty(DecompilerContext.CURRENT_VAR_PROCESSOR, varproc);

      RootStatement root = null;

      boolean isError = false;

      try {
        if (mt.containsCode()) {

          if (maxsec == 0) { // blocking wait
            root = MethodProcessorThread.codeToJava(mt, varproc);
          }
          else {
            MethodProcessorThread mtproc = new MethodProcessorThread(mt, varproc, DecompilerContext.getCurrentContext());
            Thread mtthread = new Thread(mtproc);
            long stopAt = System.currentTimeMillis() + maxsec * 1000;

            mtthread.start();

            while (mtthread.isAlive()) {

              synchronized (mtproc.lock) {
                mtproc.lock.wait(100);
              }

              if (System.currentTimeMillis() >= stopAt) {
                String message = "Processing time limit exceeded for method " + mt.getName() + ", execution interrupted.";
                DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.ERROR);
                killThread(mtthread);
                isError = true;
                break;
              }
            }

            if (!isError) {
              root = mtproc.getResult();
            }
          }
        }
        else {
          boolean thisvar = !mt.hasModifier(CodeConstants.ACC_STATIC);
          MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());

          int paramcount = 0;
          if (thisvar) {
            varproc.getThisvars().put(new VarVersionPaar(0, 0), classStruct.qualifiedName);
            paramcount = 1;
          }
          paramcount += md.params.length;

          int varindex = 0;
          for (int i = 0; i < paramcount; i++) {
            varproc.setVarName(new VarVersionPaar(varindex, 0), vc.getFreeName(varindex));

            if (thisvar) {
              if (i == 0) {
                varindex++;
              }
              else {
                varindex += md.params[i - 1].stack_size;
              }
            }
            else {
              varindex += md.params[i].stack_size;
            }
          }
        }
      }
      catch (Throwable ex) {
        DecompilerContext.getLogger().writeMessage("Method " + mt.getName() + " " + mt.getDescriptor() + " couldn't be decompiled.", ex);
        isError = true;
      }

      MethodWrapper meth = new MethodWrapper(root, varproc, mt, counter);
      meth.decompiledWithErrors = isError;

      methods.addWithKey(meth, InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));

      // rename vars so that no one has the same name as a field
      varproc.refreshVarNames(new VarNamesCollector(setFieldNames));

      // if debug information present and should be used
      if (DecompilerContext.getOption(IFernflowerPreferences.USE_DEBUG_VAR_NAMES)) {
        StructLocalVariableTableAttribute attr = (StructLocalVariableTableAttribute)mt.getAttributes().getWithKey(
          StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TABLE);

        if (attr != null) {
          varproc.setDebugVarNames(attr.getMapVarNames());
        }
      }

      DecompilerContext.getLogger().endMethod();
    }

    DecompilerContext.getLogger().endClass();
  }

  @SuppressWarnings("deprecation")
  private static void killThread(Thread thread) {
    thread.stop();
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
}
