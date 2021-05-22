// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.main.collectors.BytecodeSourceMapper;
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
import org.jetbrains.java.decompiler.struct.attr.StructEnclosingMethodAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructInnerClassesAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

public class ClassesProcessor implements CodeConstants {
  public static final int AVERAGE_CLASS_SIZE = 16 * 1024;

  private final StructContext context;
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
    this.context = context;
  }

  public void loadClasses(IIdentifierRenamer renamer) {
    Map<String, Inner> mapInnerClasses = new HashMap<>();
    Map<String, Set<String>> mapNestedClassReferences = new HashMap<>();
    Map<String, Set<String>> mapEnclosingClassReferences = new HashMap<>();
    Map<String, String> mapNewSimpleNames = new HashMap<>();

    boolean bDecompileInner = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_INNER);
    boolean verifyAnonymousClasses = DecompilerContext.getOption(IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES);

    // create class nodes
    for (StructClass cl : context.getClasses().values()) {
      if (cl.isOwn() && !mapRootClasses.containsKey(cl.qualifiedName)) {
        if (bDecompileInner) {
          StructInnerClassesAttribute inner = cl.getAttribute(StructGeneralAttribute.ATTRIBUTE_INNER_CLASSES);

          if (inner != null) {
            for (StructInnerClassesAttribute.Entry entry : inner.getEntries()) {
              String innerName = entry.innerName;

              // original simple name
              String simpleName = entry.simpleName;
              String savedName = mapNewSimpleNames.get(innerName);
              if (savedName != null) {
                simpleName = savedName;
              }
              else if (simpleName != null &&
                       renamer != null &&
                       renamer.toBeRenamed(IIdentifierRenamer.Type.ELEMENT_CLASS, simpleName, null, null)) {
                simpleName = renamer.getNextClassName(innerName, simpleName);
                mapNewSimpleNames.put(innerName, simpleName);
              }

              Inner rec = new Inner();
              rec.simpleName = simpleName;
              rec.type = entry.simpleNameIdx == 0 ? ClassNode.CLASS_ANONYMOUS : entry.outerNameIdx == 0 ? ClassNode.CLASS_LOCAL : ClassNode.CLASS_MEMBER;
              rec.accessFlags = entry.accessFlags;

              // enclosing class
              String enclClassName = entry.outerNameIdx != 0 ? entry.enclosingName : cl.qualifiedName;
              if (enclClassName == null || innerName.equals(enclClassName)) {
                continue;  // invalid name or self reference
              }
              if (rec.type == ClassNode.CLASS_MEMBER && !innerName.equals(enclClassName + '$' + entry.simpleName)) {
                continue;  // not a real inner class
              }

              StructClass enclosingClass = context.getClasses().get(enclClassName);
              if (enclosingClass != null && enclosingClass.isOwn()) { // own classes only
                Inner existingRec = mapInnerClasses.get(innerName);
                if (existingRec == null) {
                  mapInnerClasses.put(innerName, rec);
                }
                else if (!Inner.equal(existingRec, rec)) {
                  String message = "Inconsistent inner class entries for " + innerName + "!";
                  DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
                }

                // reference to the nested class
                mapNestedClassReferences.computeIfAbsent(enclClassName, k -> new HashSet<>()).add(innerName);
                // reference to the enclosing class
                mapEnclosingClassReferences.computeIfAbsent(innerName, k -> new HashSet<>()).add(enclClassName);
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
              StructInnerClassesAttribute inner = scl.getAttribute(StructGeneralAttribute.ATTRIBUTE_INNER_CLASSES);

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

                // sanity checks of the class supposed to be anonymous
                if (verifyAnonymousClasses && nestedNode.type == ClassNode.CLASS_ANONYMOUS && !isAnonymous(nestedNode.classStruct, scl)) {
                  nestedNode.type = ClassNode.CLASS_LOCAL;
                }

                if (nestedNode.type == ClassNode.CLASS_ANONYMOUS) {
                  StructClass cl = nestedNode.classStruct;
                  // remove static if anonymous class (a common compiler bug)
                  nestedNode.access &= ~CodeConstants.ACC_STATIC;

                  int[] interfaces = cl.getInterfaces();
                  if (interfaces.length > 0) {
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

  private static boolean isAnonymous(StructClass cl, StructClass enclosingCl) {
    // checking super class and interfaces
    int[] interfaces = cl.getInterfaces();
    if (interfaces.length > 0) {
      boolean hasNonTrivialSuperClass = cl.superClass != null && !VarType.VARTYPE_OBJECT.equals(new VarType(cl.superClass.getString(), true));
      if (hasNonTrivialSuperClass || interfaces.length > 1) { // can't have multiple 'sources'
        String message = "Inconsistent anonymous class definition: '" + cl.qualifiedName + "'. Multiple interfaces and/or super class defined.";
        DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
        return false;
      }
    }
    else if (cl.superClass == null) { // neither interface nor super class defined
      String message = "Inconsistent anonymous class definition: '" + cl.qualifiedName + "'. Neither interface nor super class defined.";
      DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
      return false;
    }

    // FIXME: check constructors
    // FIXME: check enclosing class/method

    ConstantPool pool = enclosingCl.getPool();

    int refCounter = 0;
    boolean refNotNew = false;

    StructEnclosingMethodAttribute attribute = cl.getAttribute(StructGeneralAttribute.ATTRIBUTE_ENCLOSING_METHOD);
    String enclosingMethod = attribute != null ? attribute.getMethodName() : null;

    // checking references in the enclosing class
    for (StructMethod mt : enclosingCl.getMethods()) {
      if (enclosingMethod != null && !enclosingMethod.equals(mt.getName())) {
        continue;
      }

      try {
        mt.expandData(enclosingCl);

        InstructionSequence seq = mt.getInstructionSequence();
        if (seq != null) {
          int len = seq.length();
          for (int i = 0; i < len; i++) {
            Instruction instr = seq.getInstr(i);
            switch (instr.opcode) {
              case opc_checkcast:
              case opc_instanceof:
                if (cl.qualifiedName.equals(pool.getPrimitiveConstant(instr.operand(0)).getString())) {
                  refCounter++;
                  refNotNew = true;
                }
                break;
              case opc_new:
              case opc_anewarray:
              case opc_multianewarray:
                if (cl.qualifiedName.equals(pool.getPrimitiveConstant(instr.operand(0)).getString())) {
                  refCounter++;
                }
                break;
              case opc_getstatic:
              case opc_putstatic:
                if (cl.qualifiedName.equals(pool.getLinkConstant(instr.operand(0)).classname)) {
                  refCounter++;
                  refNotNew = true;
                }
            }
          }
        }

        mt.releaseResources();
      }
      catch (IOException ex) {
        String message = "Could not read method while checking anonymous class definition: '" + enclosingCl.qualifiedName + "', '" +
                         InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()) + "'";
        DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
        return false;
      }

      if (refCounter > 1 || refNotNew) {
        String message = "Inconsistent references to the class '" + cl.qualifiedName + "' which is supposed to be anonymous";
        DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
        return false;
      }
    }

    return true;
  }

  public void writeClass(StructClass cl, TextBuffer buffer) throws IOException {
    ClassNode root = mapRootClasses.get(cl.qualifiedName);
    if (root.type != ClassNode.CLASS_ROOT) {
      return;
    }

    boolean packageInfo = cl.isSynthetic() && "package-info".equals(root.simpleName);
    boolean moduleInfo = cl.hasModifier(CodeConstants.ACC_MODULE) && cl.hasAttribute(StructGeneralAttribute.ATTRIBUTE_MODULE);

    DecompilerContext.getLogger().startReadingClass(cl.qualifiedName);
    try {
      ImportCollector importCollector = new ImportCollector(root);
      DecompilerContext.startClass(importCollector);

      if (packageInfo) {
        ClassWriter.packageInfoToJava(cl, buffer);

        importCollector.writeImports(buffer, false);
      }
      else if (moduleInfo) {
        TextBuffer moduleBuffer = new TextBuffer(AVERAGE_CLASS_SIZE);
        ClassWriter.moduleInfoToJava(cl, moduleBuffer);

        importCollector.writeImports(buffer, true);

        buffer.append(moduleBuffer);
      }
      else {
        new LambdaProcessor().processClass(root);

        // add simple class names to implicit import
        addClassNameToImport(root, importCollector);

        // build wrappers for all nested classes (that's where actual processing takes place)
        initWrappers(root);

        new NestedClassProcessor().processClass(root, root);

        new NestedMemberAccess().propagateMemberAccess(root);

        TextBuffer classBuffer = new TextBuffer(AVERAGE_CLASS_SIZE);
        new ClassWriter().classToJava(root, classBuffer, 0, null);

        int index = cl.qualifiedName.lastIndexOf('/');
        if (index >= 0) {
          String packageName = cl.qualifiedName.substring(0, index).replace('/', '.');
          buffer.append("package ").append(packageName).append(';').appendLineSeparator().appendLineSeparator();
        }

        importCollector.writeImports(buffer, true);

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
    }
    finally {
      destroyWrappers(root);
      DecompilerContext.getLogger().endReadingClass();
    }
  }

  private static void initWrappers(ClassNode node) {
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

  private static void addClassNameToImport(ClassNode node, ImportCollector imp) {
    if (node.simpleName != null && node.simpleName.length() > 0) {
      imp.getShortName(node.type == ClassNode.CLASS_ROOT ? node.classStruct.qualifiedName : node.simpleName, false);
    }

    for (ClassNode nd : node.nested) {
      addClassNameToImport(nd, imp);
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