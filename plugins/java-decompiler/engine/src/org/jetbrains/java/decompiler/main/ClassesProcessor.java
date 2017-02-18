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
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.collectors.BytecodeSourceMapper;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.collectors.ImportCollector;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.LambdaProcessor;
import org.jetbrains.java.decompiler.main.rels.NestedClassProcessor;
import org.jetbrains.java.decompiler.main.rels.NestedMemberAccess;
import org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructInnerClassesAttribute;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

public class ClassesProcessor {
  public static final int AVERAGE_CLASS_SIZE = 16 * 1024;

  private final Map<String, ClassNode> mapRootClasses = new HashMap<>();

  private static class Inner {
    private String simpleName;
    private int type;
    private int accessFlags;

    private static boolean equal(Inner o1, Inner o2) {
      return o1.type == o2.type && o1.accessFlags == o2.accessFlags && InterpreterUtil.equalObjects(o1.simpleName, o2.simpleName);
    }
  }

  public ClassesProcessor(StructContext context) {
    Map<String, Inner> mapInnerClasses = new HashMap<>();
    Map<String, Set<String>> mapNestedClassReferences = new HashMap<>();
    Map<String, Set<String>> mapEnclosingClassReferences = new HashMap<>();
    Map<String, String> mapNewSimpleNames = new HashMap<>();

    boolean bDecompileInner = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_INNER);

    // create class nodes
    for (StructClass cl : context.getClasses().values()) {
      if (cl.isOwn() && !mapRootClasses.containsKey(cl.qualifiedName)) {
        if (bDecompileInner) {
          StructInnerClassesAttribute inner = (StructInnerClassesAttribute)cl.getAttributes().getWithKey("InnerClasses");

          if (inner != null) {
            for (StructInnerClassesAttribute.Entry entry : inner.getEntries()) {
              String innerName = entry.innerName;

              // original simple name
              String simpleName = entry.simpleName;
              String savedName = mapNewSimpleNames.get(innerName);
              if (savedName != null) {
                simpleName = savedName;
              }
              else if (simpleName != null && DecompilerContext.getOption(IFernflowerPreferences.RENAME_ENTITIES)) {
                IIdentifierRenamer renamer = DecompilerContext.getPoolInterceptor().getHelper();
                if (renamer.toBeRenamed(IIdentifierRenamer.Type.ELEMENT_CLASS, simpleName, null, null)) {
                  simpleName = renamer.getNextClassName(innerName, simpleName);
                  mapNewSimpleNames.put(innerName, simpleName);
                }
              }

              Inner rec = new Inner();
              rec.simpleName = simpleName;
              rec.type = entry.outerNameIdx != 0 ? ClassNode.CLASS_MEMBER : entry.simpleNameIdx != 0 ? ClassNode.CLASS_LOCAL : ClassNode.CLASS_ANONYMOUS;
              rec.accessFlags = entry.accessFlags;

              // enclosing class
              String enclClassName;
              if (entry.outerNameIdx != 0) {
                enclClassName = entry.enclosingName;
              }
              else {
                enclClassName = cl.qualifiedName;
              }

              if (!innerName.equals(enclClassName)) {  // self reference
                StructClass enclosing_class = context.getClasses().get(enclClassName);
                if (enclosing_class != null && enclosing_class.isOwn()) { // own classes only

                  Inner existingRec = mapInnerClasses.get(innerName);
                  if (existingRec == null) {
                    mapInnerClasses.put(innerName, rec);
                  }
                  else if (!Inner.equal(existingRec, rec)) {
                    String message = "Inconsistent inner class entries for " + innerName + "!";
                    DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
                  }

                  // reference to the nested class
                  Set<String> set = mapNestedClassReferences.get(enclClassName);
                  if (set == null) {
                    mapNestedClassReferences.put(enclClassName, set = new HashSet<>());
                  }
                  set.add(innerName);

                  // reference to the enclosing class
                  set = mapEnclosingClassReferences.get(innerName);
                  if (set == null) {
                    mapEnclosingClassReferences.put(innerName, set = new HashSet<>());
                  }
                  set.add(enclClassName);
                }
              }
            }
          }
        }

        ClassNode node = new ClassNode(ClassNode.CLASS_ROOT, cl);
        node.access = cl.getAccessFlags();
        mapRootClasses.put(cl.qualifiedName, node);
      }
    }

    if (bDecompileInner) {
      // connect nested classes
      for (Entry<String, ClassNode> ent : mapRootClasses.entrySet()) {
        // root class?
        if (!mapInnerClasses.containsKey(ent.getKey())) {
          Set<String> setVisited = new HashSet<>();
          LinkedList<String> stack = new LinkedList<>();

          stack.add(ent.getKey());
          setVisited.add(ent.getKey());

          while (!stack.isEmpty()) {
            String superClass = stack.removeFirst();
            ClassNode superNode = mapRootClasses.get(superClass);

            Set<String> setNestedClasses = mapNestedClassReferences.get(superClass);
            if (setNestedClasses != null) {

              StructClass scl = superNode.classStruct;
              StructInnerClassesAttribute inner = (StructInnerClassesAttribute)scl.getAttributes().getWithKey("InnerClasses");

              if (inner == null || inner.getEntries().isEmpty()) {
                DecompilerContext.getLogger().writeMessage(superClass + " does not contain inner classes!", IFernflowerLogger.Severity.WARN);
                continue;
              }

              for (StructInnerClassesAttribute.Entry entry : inner.getEntries()) {
                String nestedClass = entry.innerName;
                if (!setNestedClasses.contains(nestedClass)) {
                  continue;
                }

                if (!setVisited.add(nestedClass)) {
                  continue;
                }

                ClassNode nestedNode = mapRootClasses.get(nestedClass);
                if (nestedNode == null) {
                  DecompilerContext.getLogger().writeMessage("Nested class " + nestedClass + " missing!", IFernflowerLogger.Severity.WARN);
                  continue;
                }

                Inner rec = mapInnerClasses.get(nestedClass);

                //if ((Integer)arr[2] == ClassNode.CLASS_MEMBER) {
                  // FIXME: check for consistent naming
                //}

                nestedNode.simpleName = rec.simpleName;
                nestedNode.type = rec.type;
                nestedNode.access = rec.accessFlags;

                if (nestedNode.type == ClassNode.CLASS_ANONYMOUS) {
                  StructClass cl = nestedNode.classStruct;

                  // remove static if anonymous class (a common compiler bug)
                  nestedNode.access &= ~CodeConstants.ACC_STATIC;

                  int[] interfaces = cl.getInterfaces();

                  if (interfaces.length > 0) {
                    if (interfaces.length > 1) {
                      String message = "Inconsistent anonymous class definition: " + cl.qualifiedName;
                      DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
                    }
                    nestedNode.anonymousClassType = new VarType(cl.getInterface(0), true);
                  }
                  else {
                    nestedNode.anonymousClassType = new VarType(cl.superClass.getString(), true);
                  }
                }
                else if (nestedNode.type == ClassNode.CLASS_LOCAL) {
                  // only abstract and final are permitted (a common compiler bug)
                  nestedNode.access &= (CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_FINAL);
                }

                superNode.nested.add(nestedNode);
                nestedNode.parent = superNode;

                nestedNode.enclosingClasses.addAll(mapEnclosingClassReferences.get(nestedClass));

                stack.add(nestedClass);
              }
            }
          }
        }
      }
    }
  }

  public void writeClass(StructClass cl, TextBuffer buffer) throws IOException {
    ClassNode root = mapRootClasses.get(cl.qualifiedName);
    if (root.type != ClassNode.CLASS_ROOT) {
      return;
    }

    DecompilerContext.getLogger().startReadingClass(cl.qualifiedName);
    try {
      ImportCollector importCollector = new ImportCollector(root);
      DecompilerContext.setImportCollector(importCollector);
      DecompilerContext.setCounterContainer(new CounterContainer());
      DecompilerContext.setBytecodeSourceMapper(new BytecodeSourceMapper());

      new LambdaProcessor().processClass(root);

      // add simple class names to implicit import
      addClassnameToImport(root, importCollector);

      // build wrappers for all nested classes (that's where actual processing takes place)
      initWrappers(root);

      new NestedClassProcessor().processClass(root, root);

      new NestedMemberAccess().propagateMemberAccess(root);

      TextBuffer classBuffer = new TextBuffer(AVERAGE_CLASS_SIZE);
      new ClassWriter().classToJava(root, classBuffer, 0, null);

      int index = cl.qualifiedName.lastIndexOf("/");
      if (index >= 0) {
        String packageName = cl.qualifiedName.substring(0, index).replace('/', '.');

        buffer.append("package ");
        buffer.append(packageName);
        buffer.append(";");
        buffer.appendLineSeparator();
        buffer.appendLineSeparator();
      }

      int import_lines_written = importCollector.writeImports(buffer);
      if (import_lines_written > 0) {
        buffer.appendLineSeparator();
      }

      int offsetLines = buffer.countLines();

      buffer.append(classBuffer);

      if (DecompilerContext.getOption(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING)) {
        BytecodeSourceMapper mapper = DecompilerContext.getBytecodeSourceMapper();
        mapper.addTotalOffset(offsetLines);
        if (DecompilerContext.getOption(IFernflowerPreferences.DUMP_ORIGINAL_LINES)) {
          buffer.dumpOriginalLineNumbers(mapper.getOriginalLinesMapping());
        }
        if (DecompilerContext.getOption(IFernflowerPreferences.UNIT_TEST_MODE)) {
          buffer.appendLineSeparator();
          mapper.dumpMapping(buffer, true);
        }
      }
    }
    finally {
      destroyWrappers(root);
      DecompilerContext.getLogger().endReadingClass();
    }
  }

  private static void initWrappers(ClassNode node) throws IOException {
    if (node.type == ClassNode.CLASS_LAMBDA) {
      return;
    }

    ClassWrapper wrapper = new ClassWrapper(node.classStruct);
    wrapper.init();

    node.wrapper = wrapper;

    for (ClassNode nd : node.nested) {
      initWrappers(nd);
    }
  }

  private static void addClassnameToImport(ClassNode node, ImportCollector imp) {
    if (node.simpleName != null && node.simpleName.length() > 0) {
      imp.getShortName(node.type == ClassNode.CLASS_ROOT ? node.classStruct.qualifiedName : node.simpleName, false);
    }

    for (ClassNode nd : node.nested) {
      addClassnameToImport(nd, imp);
    }
  }

  private static void destroyWrappers(ClassNode node) {
    node.wrapper = null;
    node.classStruct.releaseResources();

    for (ClassNode nd : node.nested) {
      destroyWrappers(nd);
    }
  }

  public Map<String, ClassNode> getMapRootClasses() {
    return mapRootClasses;
  }


  public static class ClassNode {
    public static final int CLASS_ROOT = 0;
    public static final int CLASS_MEMBER = 1;
    public static final int CLASS_ANONYMOUS = 2;
    public static final int CLASS_LOCAL = 4;
    public static final int CLASS_LAMBDA = 8;

    public int type;
    public int access;
    public String simpleName;
    public final StructClass classStruct;
    private ClassWrapper wrapper;
    public String enclosingMethod;
    public InvocationExprent superInvocation;
    public final Map<String, VarVersionPair> mapFieldsToVars = new HashMap<>();
    public VarType anonymousClassType;
    public final List<ClassNode> nested = new ArrayList<>();
    public final Set<String> enclosingClasses = new HashSet<>();
    public ClassNode parent;
    public LambdaInformation lambdaInformation;
    public boolean namelessConstructorStub = false;

    public ClassNode(String content_class_name,
                     String content_method_name,
                     String content_method_descriptor,
                     int content_method_invocation_type,
                     String lambda_class_name,
                     String lambda_method_name,
                     String lambda_method_descriptor,
                     StructClass classStruct) { // lambda class constructor
      this.type = CLASS_LAMBDA;
      this.classStruct = classStruct; // 'parent' class containing the static function

      lambdaInformation = new LambdaInformation();

      lambdaInformation.class_name = lambda_class_name;
      lambdaInformation.method_name = lambda_method_name;
      lambdaInformation.method_descriptor = lambda_method_descriptor;

      lambdaInformation.content_class_name = content_class_name;
      lambdaInformation.content_method_name = content_method_name;
      lambdaInformation.content_method_descriptor = content_method_descriptor;
      lambdaInformation.content_method_invocation_type = content_method_invocation_type;

      lambdaInformation.content_method_key =
        InterpreterUtil.makeUniqueKey(lambdaInformation.content_method_name, lambdaInformation.content_method_descriptor);

      anonymousClassType = new VarType(lambda_class_name, true);

      boolean is_method_reference = (content_class_name != classStruct.qualifiedName);
      if (!is_method_reference) { // content method in the same class, check synthetic flag
        StructMethod mt = classStruct.getMethod(content_method_name, content_method_descriptor);
        is_method_reference = !mt.isSynthetic(); // if not synthetic -> method reference
      }

      lambdaInformation.is_method_reference = is_method_reference;
      lambdaInformation.is_content_method_static =
        (lambdaInformation.content_method_invocation_type == CodeConstants.CONSTANT_MethodHandle_REF_invokeStatic); // FIXME: redundant?
    }

    public ClassNode(int type, StructClass classStruct) {
      this.type = type;
      this.classStruct = classStruct;

      simpleName = classStruct.qualifiedName.substring(classStruct.qualifiedName.lastIndexOf('/') + 1);
    }

    public ClassNode getClassNode(String qualifiedName) {
      for (ClassNode node : nested) {
        if (qualifiedName.equals(node.classStruct.qualifiedName)) {
          return node;
        }
      }
      return null;
    }

    public ClassWrapper getWrapper() {
      ClassNode node = this;
      while (node.type == CLASS_LAMBDA) {
        node = node.parent;
      }
      return node.wrapper;
    }

    public static class LambdaInformation {
      public String class_name;
      public String method_name;
      public String method_descriptor;

      public String content_class_name;
      public String content_method_name;
      public String content_method_descriptor;
      public int content_method_invocation_type; // values from CONSTANT_MethodHandle_REF_*
      public String content_method_key;

      public boolean is_method_reference;
      public boolean is_content_method_static;
    }
  }
}
